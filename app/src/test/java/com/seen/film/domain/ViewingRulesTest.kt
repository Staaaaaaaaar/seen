package com.seen.film.domain

import com.seen.film.data.Movie
import com.seen.film.data.MovieHistory
import com.seen.film.data.Viewing
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ViewingRulesTest {
    @org.junit.Test fun rejectsInvalidTmdbIdAndCountsSupplementaryUnicodeAsOne() {
        org.junit.Assert.assertTrue(ViewingRules.validate(ViewingDraft(title = "电影", tmdbId = 0)).containsKey("title"))
        org.junit.Assert.assertFalse(ViewingRules.validate(ViewingDraft(title = "🎬".repeat(300))).containsKey("title"))
        org.junit.Assert.assertTrue(ViewingRules.validate(ViewingDraft(title = "🎬".repeat(301))).containsKey("title"))
    }

    private val today = LocalDate.of(2026, 10, 23)
    private fun draft() = ViewingDraft(title = "电影", date = today.toString())

    @Test fun onlyTitleAndDateAreRequired() {
        assertTrue(ViewingRules.validate(draft(), today).isEmpty())
        assertNull(draft().rating)
    }
    @Test fun invalidAndFutureDatesAreRejectedWithoutTimezoneConversion() {
        assertTrue(ViewingRules.validate(draft().copy(date = "2026-02-30"), today).containsKey("date"))
        assertTrue(ViewingRules.validate(draft().copy(date = "2026-10-24"), today).containsKey("date"))
        assertFalse(ViewingRules.validate(draft().copy(date = "1999-12-31", time = "23:59"), today).containsKey("date"))
    }
    @Test fun ratingHasTenHalfStarStepsAndANullState() {
        for (value in 1..10) assertTrue(ViewingRules.validate(draft().copy(rating = value), today).isEmpty())
        for (value in listOf(0, 11)) assertTrue(ViewingRules.validate(draft().copy(rating = value), today).containsKey("rating"))
        assertEquals("4.5", ViewingRules.ratingLabel(9))
        assertEquals("未评分", ViewingRules.ratingLabel(null))
    }
    @Test fun unicodeLimitCountsCodePointsAndDoesNotSplitEmoji() {
        assertFalse(ViewingRules.validate(draft().copy(notes = "🎬".repeat(50_000)), today).containsKey("notes"))
        assertTrue(ViewingRules.validate(draft().copy(notes = "🎬".repeat(50_001)), today).containsKey("notes"))
    }
    private val movie = Movie(id = "movie", title = "同一部电影", createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z")
    private fun viewing(id: String, date: String, time: String?, score: Int? = null) = Viewing(
        id = id, movieId = movie.id, viewedDate = date, viewedTime = time, ratingHalfStars = score,
        createdAt = "2026-10-23T00:00:00Z", updatedAt = "2026-10-23T00:00:00Z",
    )
    @Test fun unknownTimeSortsAfterKnownTimeAndDoesNotBecomeMidnight() {
        val values = listOf(viewing("unknown", "2026-10-22", null), viewing("early", "2026-10-22", "09:00"), viewing("late", "2026-10-22", "23:00"))
        assertEquals(listOf("late", "early", "unknown"), values.sortedWith(viewingOrder).map { it.id })
        assertNull(values.first().viewedTime)
    }
    @Test fun repeatViewingsStayUnderOneMovieAndLatestUnratedDoesNotInheritOldRating() {
        val history = MovieHistory(movie, listOf(viewing("old", "2026-10-21", null, 10), viewing("latest", "2026-10-22", null)))
        assertEquals(1, libraryMovies(listOf(history), "", LibrarySort.RECENT).size)
        assertEquals(2, history.viewings.size)
        assertNull(history.latestRating())
        assertTrue(libraryMovies(listOf(history), "does not match", LibrarySort.RECENT).isEmpty())
        assertTrue(libraryMovies(listOf(MovieHistory(movie, emptyList())), "", LibrarySort.RECENT).isEmpty())
    }
}
