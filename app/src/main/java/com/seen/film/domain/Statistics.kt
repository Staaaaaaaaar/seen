package com.seen.film.domain

import com.seen.film.data.MovieHistory
import java.time.LocalDate
import java.time.YearMonth

enum class StatisticsPeriod { THIS_YEAR, ALL }
data class MonthCount(val month:YearMonth,val count:Int,val isFuture:Boolean)
data class GenreCount(val id:Long,val name:String,val count:Int)
data class LibraryStatistics(val movieCount:Int,val viewingCount:Int,val months:List<MonthCount>,val genres:List<GenreCount>,val unknownGenreMovieCount:Int)

fun libraryStatistics(all:List<MovieHistory>,period:StatisticsPeriod,today:LocalDate):LibraryStatistics {
    val current=YearMonth.from(today)
    val selected=all.mapNotNull{history->
        val dates=history.viewings.map{LocalDate.parse(it.viewedDate)}.filter{period==StatisticsPeriod.ALL||it.year==today.year}
        if(dates.isEmpty())null else history to dates
    }
    val counts=selected.flatMap{it.second}.groupingBy{YearMonth.from(it)}.eachCount()
    val first=if(period==StatisticsPeriod.THIS_YEAR)YearMonth.of(today.year,1) else counts.keys.minOrNull()
    val last=if(period==StatisticsPeriod.THIS_YEAR)YearMonth.of(today.year,12) else maxOf(current,counts.keys.maxOrNull()?:current)
    val months=if(first==null)emptyList() else generateSequence(first){if(it<last)it.plusMonths(1) else null}.map{MonthCount(it,counts[it]?:0,it>current)}.toList()
    val genres=selected.flatMap{it.first.genres.distinctBy{g->g.genreId}}.groupBy{it.genreId}.map{(id,items)->
        GenreCount(id,items.first().name,items.size)
    }.sortedWith(compareByDescending<GenreCount>{it.count}.thenBy{it.name}.thenBy{it.id})
    return LibraryStatistics(selected.size,selected.sumOf{it.second.size},months,genres,selected.count{it.first.genres.isEmpty()})
}
