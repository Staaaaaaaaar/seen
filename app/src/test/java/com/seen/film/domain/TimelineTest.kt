package com.seen.film.domain

import com.seen.film.data.*
import org.junit.Assert.*
import org.junit.Test

class TimelineTest {
    @Test fun mixedRecordsKeepKnownTimesFirstAndNeverCollideOnIds(){
        val movie=Movie("m","Movie",createdAt="2020",updatedAt="2020")
        val viewings=listOf(
            Viewing("early","m","2020-01-01","10:00",createdAt="2020-01-01T12:00:00Z",updatedAt="2020"),
            Viewing("same","m","2020-01-01",createdAt="2020-01-01T09:00:00Z",updatedAt="2020"))
        val notes=listOf(
            MovieNote("late","m","2020-01-01","23:00",body="Late",createdAt="2020",updatedAt="2020"),
            MovieNote("same","m","2020-01-01",body="Unknown time",createdAt="2020-01-01T08:00:00Z",updatedAt="2020"))
        val timeline=movieTimeline(MovieHistory(movie,viewings,notes))
        assertEquals(listOf("NOTE:late","VIEWING:early","VIEWING:same","NOTE:same"),timeline.map{it.key})
        assertEquals(4,timeline.map{it.key}.toSet().size)
        assertNull(timeline.last().time)
        assertEquals("Unknown time",timeline.last().body)
    }
}
