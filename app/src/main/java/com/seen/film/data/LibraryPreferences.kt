package com.seen.film.data

import android.content.Context
import com.seen.film.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("library-ui", Context.MODE_PRIVATE)
    fun read(): LibraryQuery = LibraryQuery(
        filter = runCatching { LibraryFilter.valueOf(preferences.getString("filter", "ALL")!!) }.getOrDefault(LibraryFilter.ALL),
        sort = runCatching { LibrarySort.valueOf(preferences.getString("sort", "TITLE")!!) }.getOrDefault(LibrarySort.TITLE).let{if(it==LibrarySort.ADDED)LibrarySort.TITLE else it},
        direction = runCatching { SortDirection.valueOf(preferences.getString("direction", "ASC")!!) }.getOrDefault(SortDirection.DESC))
    suspend fun save(query: LibraryQuery) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString("filter", query.filter.name).putString("sort", query.sort.name)
            .putString("direction", query.direction.name).commit()) { "无法保存影库显示偏好" }
    }
}
