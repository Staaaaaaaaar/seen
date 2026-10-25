package com.seen.film.data

import androidx.room.withTransaction
import com.seen.film.domain.ViewingDraft
import com.seen.film.domain.ViewingRules
import java.time.Instant

class SeenRepository(private val database: SeenDatabase, private val access: LibraryAccess = LibraryAccess()) {
    private val dao = database.dao()
    val library = dao.observeLibrary()
    suspend fun movie(id: String): Movie? = dao.movie(id)
    suspend fun viewing(id: String): Viewing? = dao.viewing(id)

    suspend fun save(draft: ViewingDraft): String = access.write {
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
            check(dao.movie(movieId) != null) { "电影已不存在，请重新选择" }
            val previous = dao.viewing(draft.key)
            check(!draft.isEditing || previous != null) { "这次观看已被删除" }
            val viewing = Viewing(
                id = draft.key, movieId = movieId, viewedDate = draft.date,
                viewedTime = draft.time.ifBlank { null }, location = draft.location.trim().ifEmpty { null },
                ratingHalfStars = draft.rating, notes = draft.notes.takeIf { it.isNotBlank() },
                createdAt = previous?.createdAt ?: now, updatedAt = now,
            )
            if (previous == null) dao.insertViewing(viewing) else check(dao.updateViewing(viewing) == 1)
            movieId
        }
    }
    suspend fun deleteViewing(id: String) = access.write { dao.deleteViewing(id); Unit }
}
