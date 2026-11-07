package com.seen.film.domain

import com.seen.film.data.MovieHistory
import com.seen.film.data.Viewing
import java.text.Collator
import java.util.Locale

val viewingOrder: Comparator<Viewing> = compareByDescending<Viewing> { it.viewedDate }
    .thenByDescending { it.viewedTime ?: "" }.thenByDescending { it.createdAt }.thenBy { it.id }
fun MovieHistory.sortedViewings(): List<Viewing> = viewings.sortedWith(viewingOrder)
/** Historical display rule, retained for migration/legacy callers only. */
fun MovieHistory.latestRating(): Int? = sortedViewings().firstOrNull()?.ratingHalfStars

enum class LibraryFilter(val label: String) { ALL("全部"), SEEN("已看"), UNSEEN("未看") }
enum class LibrarySort(val label: String) {
    ADDED("添加时间"), TITLE("电影片名"), YEAR("上映年份"), RATING("个人评分"), VIEWED("最近观看"),
    RECENT("最近观看"), // Legacy source compatibility; never offered by the new UI.
}
enum class SortDirection { ASC, DESC }
data class LibraryQuery(val text: String = "", val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.ADDED, val direction: SortDirection = SortDirection.DESC)

fun libraryMovies(all: List<MovieHistory>, query: LibraryQuery, languageCode: String = "zh-CN"): List<MovieHistory> {
    val text = query.text.trim()
    val selected = all.filter { h ->
        (query.filter == LibraryFilter.ALL || (h.viewings.isNotEmpty() == (query.filter == LibraryFilter.SEEN))) &&
            (text.isEmpty() || listOfNotNull(h.movie.title, h.movie.originalTitle, h.movie.directorNames)
                .plus(h.genres.map { it.name }).any { it.contains(text, true) })
    }
    val collator = Collator.getInstance(Locale.forLanguageTag(languageCode))
    val descending = query.direction == SortDirection.DESC
    fun <T : Comparable<T>> nullable(a: T?, b: T?): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        descending -> b.compareTo(a)
        else -> a.compareTo(b)
    }
    fun latest(h: MovieHistory): String? = h.sortedViewings().firstOrNull()?.let { it.viewedDate + "T" + it.viewedTime.orEmpty() }
    return selected.sortedWith { a, b ->
        val primary = when (query.sort) {
            LibrarySort.ADDED -> nullable(a.movie.createdAt, b.movie.createdAt)
            LibrarySort.TITLE -> collator.compare(a.movie.title, b.movie.title) * if (descending) -1 else 1
            LibrarySort.YEAR -> nullable(a.movie.releaseYear, b.movie.releaseYear)
            LibrarySort.RATING -> nullable(a.movie.personalRatingHalfStars, b.movie.personalRatingHalfStars)
            LibrarySort.VIEWED, LibrarySort.RECENT -> nullable(latest(a), latest(b))
        }
        if (primary != 0) primary else {
            val added = b.movie.createdAt.compareTo(a.movie.createdAt)
            if (added != 0) added else a.movie.id.compareTo(b.movie.id)
        }
    }
}

/** Compatibility while historical callers and previews transition to LibraryQuery. */
fun libraryMovies(all: List<MovieHistory>, query: String, sort: LibrarySort): List<MovieHistory> =
    libraryMovies(all, LibraryQuery(query, LibraryFilter.SEEN, sort,
        if (sort == LibrarySort.TITLE) SortDirection.ASC else SortDirection.DESC))
