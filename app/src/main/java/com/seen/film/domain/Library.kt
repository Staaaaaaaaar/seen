package com.seen.film.domain

import com.seen.film.data.MovieHistory
import com.seen.film.data.Viewing

val viewingOrder: Comparator<Viewing> = compareByDescending<Viewing> { it.viewedDate }
    .thenByDescending { it.viewedTime ?: "" }
    .thenByDescending { it.createdAt }
    .thenBy { it.id }

fun MovieHistory.sortedViewings(): List<Viewing> = viewings.sortedWith(viewingOrder)
fun MovieHistory.latestRating(): Int? = sortedViewings().firstOrNull()?.ratingHalfStars

enum class LibrarySort(val label: String) { RECENT("最近观看"), TITLE("片名"), YEAR("上映年份") }

fun libraryMovies(all: List<MovieHistory>, query: String, sort: LibrarySort): List<MovieHistory> {
    val q = query.trim()
    val filtered = all.filter { it.viewings.isNotEmpty() &&
        (q.isEmpty() || it.movie.title.contains(q, ignoreCase = true) ||
            it.movie.originalTitle.orEmpty().contains(q, ignoreCase = true)) }
    return when (sort) {
        LibrarySort.TITLE -> filtered.sortedWith(compareBy<MovieHistory> { it.movie.title }.thenBy { it.movie.id })
        LibrarySort.YEAR -> filtered.sortedWith(compareByDescending<MovieHistory> { it.movie.releaseYear ?: 0 }.thenBy { it.movie.id })
        LibrarySort.RECENT -> filtered.sortedWith(compareByDescending<MovieHistory> { item -> item.viewings.maxOf { it.viewedDate } }
            .thenByDescending { item -> item.viewings.maxOf { it.createdAt } }.thenBy { it.movie.id })
    }
}
