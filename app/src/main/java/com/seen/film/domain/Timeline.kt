package com.seen.film.domain

import com.seen.film.data.MovieHistory

data class TimelineEntry(val kind:RecordKind,val id:String,val date:String,val time:String?,val location:String?,val body:String?,val createdAt:String){
    val key get()="${kind.name}:$id"
}
fun movieTimeline(history:MovieHistory):List<TimelineEntry> =
    (history.viewings.map{TimelineEntry(RecordKind.VIEWING,it.id,it.viewedDate,it.viewedTime,it.location,it.notes,it.createdAt)} +
        history.notes.map{TimelineEntry(RecordKind.NOTE,it.id,it.recordedDate,it.recordedTime,it.location,it.body,it.createdAt)})
        .sortedWith(compareByDescending<TimelineEntry>{it.date}.thenByDescending{it.time?:""}.thenByDescending{it.createdAt}.thenBy{it.id}.thenBy{it.kind.name})
