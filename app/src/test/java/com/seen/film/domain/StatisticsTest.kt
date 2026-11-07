package com.seen.film.domain

import com.seen.film.data.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class StatisticsTest {
    private val today=LocalDate.of(2026,6,15)
    private fun film(id:String,dates:List<String>,genres:List<Pair<Long,String>> = emptyList()):MovieHistory = MovieHistory(
        Movie(id,id,personalRatingHalfStars=9,createdAt="2020",updatedAt="2020"),
        dates.mapIndexed{i,d->Viewing("$id-$i",id,d,createdAt="2020",updatedAt="2020")},
        listOf(MovieNote("note-$id",id,"2026-05-01",body="Only a thought",createdAt="2020",updatedAt="2020")),
        genres.mapIndexed{i,g->MovieGenre(id,g.first,g.second,i)})
    @Test fun repeatsGenresAndNotesHaveDifferentCountingUnits(){
        val all=listOf(film("a",listOf("2025-12-01","2026-01-01","2026-05-01"),listOf(1L to "剧情",2L to "喜剧")),film("b",listOf("2026-05-02"),listOf(1L to "剧情")),film("c",listOf("2026-02-01")),film("unseen",emptyList(),listOf(3L to "动画")))
        val s=libraryStatistics(all,StatisticsPeriod.THIS_YEAR,today)
        assertEquals(3,s.movieCount);assertEquals(4,s.viewingCount)
        assertEquals(listOf(1L,2L),s.genres.map{it.id});assertEquals(listOf(2,1),s.genres.map{it.count})
        assertEquals(1,s.unknownGenreMovieCount);assertEquals(12,s.months.size)
        assertEquals(2,s.months.first{it.month==YearMonth.of(2026,5)}.count)
        assertTrue(s.months.last().isFuture);assertFalse(s.months[5].isFuture)
        val total=libraryStatistics(all,StatisticsPeriod.ALL,today)
        assertEquals(5,total.viewingCount);assertEquals(7,total.months.size)
        assertEquals(YearMonth.of(2025,12),total.months.first().month)
    }
    @Test fun emptyLibraryAndFutureLegacyDatesRemainExplicit(){
        val empty=libraryStatistics(emptyList(),StatisticsPeriod.ALL,today)
        assertEquals(0,empty.movieCount);assertEquals(0,empty.viewingCount);assertTrue(empty.months.isEmpty())
        val future=libraryStatistics(listOf(film("future",listOf("2027-02-01"))),StatisticsPeriod.ALL,today)
        assertEquals(YearMonth.of(2027,2),future.months.last().month);assertTrue(future.months.last().isFuture)
    }
}
