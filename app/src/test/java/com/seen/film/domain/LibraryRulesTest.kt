package com.seen.film.domain

import com.seen.film.data.*
import org.junit.Assert.*
import org.junit.Test

class LibraryRulesTest {
    private fun movie(id:String,title:String,added:String,rating:Int?=null,year:Int?=null)=
        Movie(id,title,releaseYear=year,createdAt=added,updatedAt="2099-01-01T00:00:00Z",personalRatingHalfStars=rating)
    private val a=MovieHistory(movie("a","Alpha","2020-01-01",9,2020),emptyList(),notes=listOf(MovieNote("n","a","2020-01-01",body="Just a note",createdAt="2020-01-01",updatedAt="2020-01-01")))
    private val b=MovieHistory(movie("b","Beta","2021-01-01",null,null),listOf(Viewing("v","b","2022-01-01",createdAt="2022-01-01",updatedAt="2022-01-01")))
    private val c=MovieHistory(movie("c","Gamma","2019-01-01",5,2010),listOf(Viewing("w","c","2023-01-01",createdAt="2023-01-01",updatedAt="2023-01-01")))
    private val all=listOf(a,b,c)
    @Test fun stateDependsOnlyOnViewingNotNotesOrScore(){
        assertEquals(listOf("c","b"),libraryMovies(all,LibraryQuery(filter=LibraryFilter.SEEN,sort=LibrarySort.VIEWED)).map{it.movie.id})
        assertEquals(listOf("a"),libraryMovies(all,LibraryQuery(filter=LibraryFilter.UNSEEN)).map{it.movie.id})
        assertEquals(listOf("b","a","c"),libraryMovies(all,LibraryQuery()).map{it.movie.id})
    }
    @Test fun allSortDirectionsKeepMissingValuesLast(){
        fun sorted(sort:LibrarySort,direction:SortDirection)=libraryMovies(all,LibraryQuery(sort=sort,direction=direction)).map{it.movie.id}
        assertEquals(listOf("c","a","b"),sorted(LibrarySort.ADDED,SortDirection.ASC))
        assertEquals(listOf("a","b","c"),sorted(LibrarySort.TITLE,SortDirection.ASC))
        assertEquals(listOf("c","b","a"),sorted(LibrarySort.TITLE,SortDirection.DESC))
        assertEquals(listOf("c","a","b"),sorted(LibrarySort.YEAR,SortDirection.ASC))
        assertEquals(listOf("a","c","b"),sorted(LibrarySort.YEAR,SortDirection.DESC))
        assertEquals(listOf("c","a","b"),sorted(LibrarySort.RATING,SortDirection.ASC))
        assertEquals(listOf("a","c","b"),sorted(LibrarySort.RATING,SortDirection.DESC))
        assertEquals(listOf("b","c","a"),sorted(LibrarySort.VIEWED,SortDirection.ASC))
        assertEquals(listOf("c","b","a"),sorted(LibrarySort.VIEWED,SortDirection.DESC))
    }
    @Test fun searchAndFilterIntersectAndTiesAreStable(){
        val annotated=a.copy(movie=a.movie.copy(directorNames="A Director"),genres=listOf(MovieGenre("a",18,"剧情",0)))
        assertEquals(listOf("a"),libraryMovies(listOf(annotated,b),LibraryQuery(text="director",filter=LibraryFilter.UNSEEN)).map{it.movie.id})
        assertEquals(listOf("a"),libraryMovies(listOf(annotated,b),LibraryQuery(text="剧情")).map{it.movie.id})
        assertTrue(libraryMovies(listOf(annotated,b),LibraryQuery(text="剧情",filter=LibraryFilter.SEEN)).isEmpty())
        val tie=a.copy(movie=a.movie.copy(id="z"))
        assertEquals(listOf("a","z"),libraryMovies(listOf(tie,a),LibraryQuery()).map{it.movie.id})
    }
}
