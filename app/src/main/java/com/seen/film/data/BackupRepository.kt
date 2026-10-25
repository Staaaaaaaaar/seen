package com.seen.film.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.seen.film.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PreparedExport internal constructor(internal val directory: File, internal val file: File)
class RestorePreview internal constructor(
    internal val directory: File, internal val file: File, internal val digest: String,
    internal val token: Any, internal val data: BackupData,
    internal val mediaDigests: Map<String, String>,
    val currentMovies: Int, val currentViewings: Int,
) {
    val appVersion get() = data.appVersion
    val exportedAt get() = data.exportedAt
    val movieCount get() = data.movies.size
    val viewingCount get() = data.viewings.size
    val posterCount get() = data.assets.size
    val hasFutureDates get() = data.viewings.any { LocalDate.parse(it.viewedDate) > LocalDate.now() }
}

/** File preparation is private; SQLite is the sole commit point. Old media is never removed. */
class BackupRepository(
    private val context: Context, private val database: SeenDatabase,
    private val posters: PosterStore, private val drafts: DraftStore, private val access: LibraryAccess,
    internal val openOutput: (Uri) -> java.io.OutputStream? = { context.contentResolver.openOutputStream(it, "wt") },
    internal val checkpoint: suspend (String) -> Unit = {},
) {
    private val codec = BackupCodec()
    private fun operation() = File(context.cacheDir, "backup-${UUID.randomUUID()}").apply { check(mkdirs()) }
    suspend fun prepareExport(): PreparedExport = withContext(Dispatchers.IO) {
        val directory = operation()
        try {
            access.snapshot {
                val (movies, viewings) = database.withTransaction {
                    require(database.dao().movieTextBytes() + database.dao().viewingTextBytes() <= BackupRules.MAX_BYTES) { "库中文本超过备份上限" }
                    database.dao().allMovies() to database.dao().allViewings()
                }
                require(movies.size <= 10000 && viewings.size <= 100000) { "电影或观看数量超过备份上限" }
                val keys = movies.mapNotNull { it.posterKey }.distinct().associateWith { UUID.randomUUID().toString() }
                val assets = keys.map { (key, id) -> id to (posters.file(key) ?: error("电影海报缺失，无法导出完整备份")) }.toMap()
                val data = BackupData(BuildConfig.VERSION_NAME, Instant.now().toString(), movies.map { it.copy(posterKey = it.posterKey?.let(keys::getValue)) }, viewings, assets)
                data.movies.forEach(BackupRules::movie); data.viewings.forEach(BackupRules::viewing)
                BackupRules.relationships(data.movies, viewings, assets.keys)
                val file = File(directory, "backup.json"); codec.write(file, data)
                val validation = File(directory, "validation").apply { mkdirs() }
                codec.read(file, validation); validation.deleteRecursively()
                PreparedExport(directory, file)
            }
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }
    suspend fun finishExport(prepared: PreparedExport, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val output = openOutput(uri) ?: error("文档提供器拒绝写入")
            output.use { stream -> prepared.file.inputStream().use { it.copyTo(stream) }; stream.flush() }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw java.io.IOException("导出失败，文档提供器可能留下不完整文件", e)
        } finally { runCatching { prepared.directory.deleteRecursively() } }
    }
    suspend fun cancelExport(prepared: PreparedExport) = withContext(NonCancellable + Dispatchers.IO) { runCatching { prepared.directory.deleteRecursively() }; Unit }
    suspend fun preview(uri: Uri): RestorePreview = withContext(Dispatchers.IO) {
        val token = access.reserve { check(drafts.read() == null) { "请先保存或放弃未完成的编辑，再恢复备份" } }
        var directory: File? = null
        try {
            val workspace = operation(); directory = workspace
            val file = File(workspace, "backup.json")
            context.contentResolver.openInputStream(uri)?.use { input ->
                LimitedOutput(file.outputStream()).use { output -> input.copyTo(output) }
            } ?: error("无法读取备份文件")
            val data = codec.read(file, workspace)
            val counts = database.withTransaction { database.dao().movieCount() to database.dao().viewingCount() }
            RestorePreview(workspace, file, file.sha256(), token, data, data.assets.mapValues { it.value.sha256() }, counts.first, counts.second)
        } catch (e: Exception) {
            withContext(NonCancellable) { access.release(token); directory?.deleteRecursively() }; throw e
        }
    }
    suspend fun cancel(preview: RestorePreview) = withContext(NonCancellable + Dispatchers.IO) {
        access.release(preview.token); runCatching { preview.directory.deleteRecursively() }; Unit
    }
    suspend fun restore(preview: RestorePreview) = withContext(Dispatchers.IO) {
        // Cancellation must not turn a completed commit into an apparent rollback.
        // Once confirmation starts, finish the bounded commit and return its known outcome.
        withContext(NonCancellable) {
            access.restoring(preview.token) {
                check(drafts.read() == null) { "存在未完成的编辑，无法覆盖恢复" }
                check(preview.file.sha256() == preview.digest) { "备份暂存内容已改变，请重新选择" }
                for ((id, file) in preview.data.assets) {
                    check(file.sha256() == preview.mediaDigests.getValue(id)) { "暂存海报已改变，请重新选择" }
                    codec.validateImage(file, preview.mediaDigests.getValue(id))
                }
                val created = mutableListOf<String>()
                try {
                    val mapping = linkedMapOf<String, String>()
                    for ((id, file) in preview.data.assets) mapping[id] = posters.importBackup(file) { created += it }
                    checkpoint("media-ready")
                    database.withTransaction {
                        val dao = database.dao(); dao.deleteAllViewings(); dao.deleteAllMovies()
                        checkpoint("sql-deleted")
                        preview.data.movies.forEach { dao.insertMovie(it.copy(posterKey = it.posterKey?.let(mapping::getValue))) }
                        checkpoint("sql-movies")
                        preview.data.viewings.forEach { dao.insertViewing(it) }
                    }
                    // Test observer failures cannot turn a committed restore into an apparent failure.
                    runCatching { checkpoint("committed") }
                } catch (e: Exception) {
                    // The commit outcome may be unknown. Only current DB references authorize deletion.
                    // If the read itself fails, retain every new file and preserve the original error.
                    runCatching {
                        val referenced = database.dao().posterKeys().toSet()
                        created.filterNot { it in referenced }.forEach { posters.file(it)?.delete() }
                    }
                    throw e
                }
            }
            access.release(preview.token)
            runCatching { preview.directory.deleteRecursively() }
        }
    }
}
