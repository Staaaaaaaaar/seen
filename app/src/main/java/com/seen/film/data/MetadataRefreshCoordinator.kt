package com.seen.film.data

import android.content.Context
import android.util.AtomicFile
import androidx.room.withTransaction
import com.seen.film.domain.MetadataLanguage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

sealed interface MetadataRefreshState {
    data object Idle : MetadataRefreshState
    data class Preparing(val done: Int, val total: Int, val target: MetadataLanguage) : MetadataRefreshState
    data class Failed(val message: String) : MetadataRefreshState
    data object Interrupted : MetadataRefreshState
    data class Completed(val language: MetadataLanguage) : MetadataRefreshState
}

/** Journaled media preparation; Room is the only publication point. */
class MetadataRefreshCoordinator(
    context: Context,
    private val database: SeenDatabase,
    private val posters: PosterStore,
    private val drafts: DraftStore,
    private val access: LibraryAccess,
    private val tmdb: TmdbRepository,
    internal val checkpoint: suspend (String) -> Unit = {},
) {
    private val directory = File(context.filesDir, "metadata-refresh")
    private val manifest get() = AtomicFile(File(directory, "manifest.json"))
    private val operation = Mutex()
    @Volatile private var running: Job? = null
    private val mutableState = MutableStateFlow<MetadataRefreshState>(MetadataRefreshState.Idle)
    val state: StateFlow<MetadataRefreshState> = mutableState.asStateFlow()
    private data class Prepared(val movieId: String, val remote: TmdbMovie, val posterKey: String?)

    suspend fun refresh(target: MetadataLanguage) = withContext(Dispatchers.IO) {
        check(operation.tryLock()) { "已有进行中的资料更新" }
        running = currentCoroutineContext().job
        var token: Any? = null
        var before: Pair<List<Movie>, LibraryMetadataState>? = null
        val created = mutableListOf<String>()
        var published = false
        try {
            token = access.reserveMetadata()
            before = access.snapshot { database.withTransaction { database.dao().allMovies() to (database.dao().metadataState() ?: LibraryMetadataState()) } }
            require(before.first.size <= 10000) { "电影数量超过资料准备上限" }
            val candidates = before.first.filter { it.tmdbId != null }
            require(!directory.exists() || !journalExists()) { "上次资料准备尚未清理，请重新打开应用后重试" }
            check(directory.mkdirs() || directory.isDirectory)
            journal(target, before.second.generation + 1, created)
            mutableState.value = MetadataRefreshState.Preparing(0, candidates.size, target)
            checkpoint("preparing")
            val prepared = mutableListOf<Prepared>()
            var totalBytes = 0L
            var totalCredits = 0
            var totalGenres = 0
            for ((index, movie) in candidates.withIndex()) {
                currentCoroutineContext().ensureActive()
                val remote = tmdb.details(movie.tmdbId!!, target)
                require(remote.id == movie.tmdbId) { "TMDB 返回了不同电影的资料" }
                totalCredits += remote.credits.size; totalGenres += remote.genres.size
                require(totalCredits <= 100000 && totalGenres <= 100000) { "电影扩展资料数量超限" }
                totalBytes += estimateBytes(remote)
                require(totalBytes <= MAX_STAGING_BYTES) { "资料准备超过 256 MiB，请减少电影数量后重试" }
                val key = remote.posterPath?.let { path ->
                    val bytes = tmdb.poster(path)
                    require(totalBytes + bytes.size <= MAX_STAGING_BYTES) { "资料准备超过 256 MiB" }
                    posters.importDownloaded(bytes) { newKey ->
                        created += newKey
                        journal(target, before.second.generation + 1, created)
                    }.also { newKey ->
                        val size = posters.file(newKey)?.length() ?: error("准备中的海报丢失")
                        require(size <= BackupRules.MAX_IMAGE) { "准备后的海报超过 2 MiB" }
                        totalBytes += size
                    }
                }
                require(totalBytes <= MAX_STAGING_BYTES) { "资料准备超过 256 MiB" }
                prepared += Prepared(movie.id, remote, key)
                mutableState.value = MetadataRefreshState.Preparing(index + 1, candidates.size, target)
            }
            checkpoint("media-ready")
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                access.publishMetadata(token) {
                    database.withTransaction {
                        val dao = database.dao()
                        check((dao.metadataState() ?: LibraryMetadataState()).generation == before.second.generation) { "电影资料版本已改变，请重试" }
                        val now = Instant.now().toString()
                        for (item in prepared) {
                            val r = item.remote
                            check(dao.updateLocalizedMovie(item.movieId, r.title, r.originalTitle, r.releaseDate?.take(4)?.toIntOrNull(),
                                r.releaseDate, r.directors, r.runtime, r.countries, r.overview, item.posterKey, target.apiCode, now) == 1) { "电影已不存在" }
                            dao.deleteMovieGenres(item.movieId); dao.deleteMovieCredits(item.movieId)
                            dao.insertGenres(r.genres.mapIndexed { index, g -> MovieGenre(item.movieId, g.id, g.name, index) })
                            dao.insertCredits(r.credits.map { c -> MovieCredit(item.movieId, c.creditKey, c.personId, c.name, c.isCast, c.department, c.job, c.character, c.profilePath, c.position) })
                        }
                        dao.putMetadataState(LibraryMetadataState(activeLanguage = target.apiCode, generation = before.second.generation + 1))
                    }
                }
                published = true
                mutableState.value = MetadataRefreshState.Completed(target)
                checkpoint("committed")
            }
        } catch (e: CancellationException) {
            if (!published) mutableState.value = MetadataRefreshState.Idle
            throw e
        } catch (e: Exception) {
            // Read the actual commit point before describing an uncertain outcome.
            published = before?.let { old -> runCatching { database.dao().metadataState()?.let { it.activeLanguage == target.apiCode && it.generation == old.second.generation + 1 } }.getOrNull() == true } ?: false
            mutableState.value = if (published) MetadataRefreshState.Completed(target) else MetadataRefreshState.Failed(
                if (e is IllegalArgumentException || e is IllegalStateException) e.message ?: "资料准备失败，原有资料未改变" else "资料准备失败，原有资料未改变，请检查网络或存储后重试")
        } finally {
            withContext(NonCancellable) {
                if (token != null) {
                    runCatching { access.snapshot {
                        val refs = liveReferences()
                        val possible = created + if (published) before?.first.orEmpty().mapNotNull { it.posterKey } else emptyList()
                        possible.distinct().filterNot { it in refs }.forEach { posters.file(it)?.delete() }
                    } }
                    runCatching { manifest.delete(); directory.delete() }
                    access.releaseMetadata(token)
                }
                running = null; operation.unlock()
            }
        }
    }

    suspend fun cancel() { running?.cancelAndJoin() }

    suspend fun recoverInterrupted() = withContext(Dispatchers.IO) {
        if (!journalExists()) return@withContext
        check(operation.tryLock()) { "已有进行中的资料更新" }
        var token: Any? = null
        try {
            token = access.reserveMetadata()
            require(manifest.baseFile.length() <= 1024 * 1024) { "准备记录过大" }
            val json = JSONObject(manifest.openRead().bufferedReader().use { it.readText() })
            val keys = json.getJSONArray("created"); require(keys.length() <= 10000)
            access.snapshot {
                val refs = liveReferences()
                for (i in 0 until keys.length()) { val key = keys.getString(i); if (key !in refs) posters.file(key)?.delete() }
            }
            val current = database.dao().metadataState()
            mutableState.value = if (current?.generation == json.getLong("generation") && current.activeLanguage == json.getString("language"))
                MetadataRefreshState.Completed(MetadataLanguage.fromCode(current.activeLanguage)) else MetadataRefreshState.Interrupted
            manifest.delete(); directory.delete()
        } catch (_: Exception) {
            mutableState.value = MetadataRefreshState.Failed("无法清理上次资料准备，请保留数据并重试")
        } finally {
            withContext(NonCancellable) { token?.let { access.releaseMetadata(it) }; operation.unlock() }
        }
    }

    private suspend fun liveReferences(): Set<String> = database.withTransaction {
        (database.dao().posterKeys() + database.dao().archivedPosterKeys()).toSet()
    } + listOfNotNull(drafts.read()?.posterKey)

    private fun journalExists(): Boolean = manifest.baseFile.exists() ||
        File(directory, "manifest.json.bak").exists() || File(directory, "manifest.json.new").exists()

    private fun journal(target: MetadataLanguage, generation: Long, created: List<String>) {
        val json = JSONObject().put("language", target.apiCode).put("generation", generation).put("created", JSONArray(created))
        val file = manifest; val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }

    private fun estimateBytes(movie: TmdbMovie): Long {
        val strings = listOfNotNull(movie.title, movie.originalTitle, movie.overview, movie.directors, movie.countries) +
            movie.genres.map { it.name } + movie.credits.flatMap { listOfNotNull(it.creditKey, it.name, it.department, it.job, it.character, it.profilePath) }
        return strings.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() * 2 } + movie.credits.size * 256L + movie.genres.size * 128L + 1024
    }
    companion object { private const val MAX_STAGING_BYTES = 256L * 1024 * 1024 }
}
