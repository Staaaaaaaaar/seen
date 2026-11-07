package com.seen.film.data

import androidx.room.withTransaction
import com.seen.film.domain.RecordDraft
import com.seen.film.domain.RecordKind
import com.seen.film.domain.RecordRules
import com.seen.film.domain.ViewingDraft
import com.seen.film.domain.ViewingRules
import java.time.Instant
import kotlinx.coroutines.flow.map

class SeenRepository(private val database: SeenDatabase, private val access: LibraryAccess = LibraryAccess()) {
    private val dao = database.dao()
    val snapshots = dao.observeLibraryChanges().map {
        database.withTransaction { LibrarySnapshot(dao.allHistories(), dao.metadataState() ?: LibraryMetadataState()) }
    }
    val library = snapshots.map { it.movies }
    suspend fun movie(id: String): Movie? = dao.movie(id)
    suspend fun viewing(id: String): Viewing? = dao.viewing(id)
    suspend fun note(id: String): MovieNote? = dao.note(id)

    suspend fun addMovie(draft: ViewingDraft): String = access.write(LibraryWriteKind.CATALOG) {
        require(ViewingRules.validate(draft.copy(forWatchlist = true)).isEmpty()) { "请检查电影资料" }
        database.withTransaction {
            val existing = draft.tmdbId?.let { dao.movieByTmdbId(it) } ?: dao.movie(draft.movieId)
            if (existing != null) return@withTransaction existing.id
            val now = Instant.now().toString()
            dao.insertMovie(Movie(id = draft.movieId, title = draft.title.trim(),
                originalTitle = draft.originalTitle.trim().ifEmpty { null }, releaseYear = draft.year.toIntOrNull(),
                releaseDate = draft.releaseDate, tmdbId = draft.tmdbId, posterKey = draft.posterKey,
                source = if (draft.tmdbId == null) "MANUAL" else "TMDB", directorNames = draft.directorNames,
                runtimeMinutes = draft.runtimeMinutes, productionCountries = draft.productionCountries,
                createdAt = now, updatedAt = now, metadataLanguage = draft.metadata?.language?.apiCode ?: if (draft.tmdbId != null) "zh-CN" else null,
                overview = draft.metadata?.overview))
            draft.metadata?.let { metadata ->
                require(metadata.id == draft.tmdbId) { "电影资料编号不匹配" }
                dao.insertGenres(metadata.genres.mapIndexed { index,g -> MovieGenre(draft.movieId,g.id,g.name,index) })
                dao.insertCredits(metadata.credits.map { c -> MovieCredit(draft.movieId,c.creditKey,c.personId,c.name,c.isCast,c.department,c.job,c.character,c.profilePath,c.position) })
            }
            draft.movieId
        }
    }

    suspend fun setPersonalRating(movieId: String, halfStars: Int?) = access.write {
        require(halfStars == null || halfStars in 1..10) { "评分为 0.5–5 星" }
        check(dao.setRating(movieId, halfStars, Instant.now().toString()) == 1) { "电影已不存在" }
    }

    suspend fun saveRecord(draft: RecordDraft): String = access.write {
        require(RecordRules.validate(draft).isEmpty()) { "请检查输入内容" }
        database.withTransaction {
            check(dao.movie(draft.movieId) != null) { "电影已不存在，请重新选择" }
            val now = Instant.now().toString()
            val time = draft.time.ifBlank { null }
            val location = draft.location.trim().ifEmpty { null }
            when (draft.kind) {
                RecordKind.VIEWING -> {
                    val old = dao.viewing(draft.key)
                    check(!draft.isEditing || old != null) { "观看记录已不存在" }
                    check(old == null || old.movieId == draft.movieId) { "记录不属于这部电影" }
                    val value = Viewing(draft.key, draft.movieId, draft.date, time, location,
                        old?.ratingHalfStars, draft.body.takeIf { it.isNotBlank() }, old?.createdAt ?: now, now)
                    if (old == null) dao.insertViewing(value) else check(dao.updateViewing(value) == 1)
                }
                RecordKind.NOTE -> {
                    val old = dao.note(draft.key)
                    check(!draft.isEditing || old != null) { "留言已不存在" }
                    check(old == null || old.movieId == draft.movieId) { "留言不属于这部电影" }
                    val value = MovieNote(draft.key, draft.movieId, draft.date, time, location,
                        draft.body, old?.createdAt ?: now, now)
                    if (old == null) dao.insertNote(value) else check(dao.updateNote(value) == 1)
                }
            }
            draft.key
        }
    }

    suspend fun deleteRecord(kind: RecordKind, id: String) = access.write {
        when (kind) {
            RecordKind.VIEWING -> dao.deleteViewing(id)
            RecordKind.NOTE -> dao.deleteNote(id)
        }
        Unit
    }

    // Transitional compatibility for saved drafts and the previous editor.
    suspend fun save(draft: ViewingDraft): String = access.write(if (draft.isNewMovie) LibraryWriteKind.CATALOG else LibraryWriteKind.PERSONAL) {
        require(ViewingRules.validate(draft).isEmpty()) { "请检查输入内容" }
        database.withTransaction {
            val now = Instant.now().toString()
            val movieId = draft.tmdbId?.let { dao.movieByTmdbId(it)?.id } ?: draft.movieId
            if (draft.isNewMovie && dao.movie(movieId) == null) {
                dao.insertMovie(Movie(
                    id = movieId, title = draft.title.trim(), originalTitle = draft.originalTitle.trim().ifEmpty { null },
                    tmdbId = draft.tmdbId, source = if (draft.tmdbId == null) "MANUAL" else "TMDB",
                    releaseDate = draft.releaseDate, directorNames = draft.directorNames,
                    runtimeMinutes = draft.runtimeMinutes, productionCountries = draft.productionCountries,
                    releaseYear = draft.year.toIntOrNull(), posterKey = draft.posterKey, createdAt = now, updatedAt = now,
                ))
            }
            draft.posterKey?.let { dao.insertArchivedPosters(listOf(ArchivedPoster(movieId,it))) }
            check(dao.movie(movieId) != null) { "电影已不存在，请重新选择" }
            if (draft.forWatchlist) {
                check(dao.setWanted(movieId, true) == 1)
                return@withTransaction movieId
            }
            val previous = dao.viewing(draft.key)
            check(!draft.isEditing || previous != null) { "这次观看已被删除" }
            val viewing = Viewing(
                id = draft.key, movieId = movieId, viewedDate = draft.date,
                viewedTime = draft.time.ifBlank { null }, location = draft.location.trim().ifEmpty { null },
                ratingHalfStars = draft.rating, notes = draft.notes.takeIf { it.isNotBlank() },
                createdAt = previous?.createdAt ?: now, updatedAt = now,
            )
            if (previous == null) dao.insertViewing(viewing) else check(dao.updateViewing(viewing) == 1)
            if (draft.legacyRatingPending) dao.setRating(movieId, draft.rating, now)
            if (!draft.isEditing) dao.setWanted(movieId, false)
            movieId
        }
    }
    suspend fun removeFromWatchlist(id: String) = access.write {
        check(dao.setWanted(id, false) == 1) { "电影已不存在" }
    }
    suspend fun deleteMovie(id: String) = access.write(LibraryWriteKind.CATALOG) {
        database.withTransaction {
            dao.deleteMovieNotes(id)
            dao.deleteMovieGenres(id)
            dao.deleteMovieCredits(id)
            dao.deleteMovieArchivedPosters(id)
            dao.deleteMovieViewings(id)
            check(dao.deleteMovie(id) == 1) { "电影已不存在" }
        }
    }
    suspend fun deleteViewing(id: String) = access.write { dao.deleteViewing(id); Unit }
}
