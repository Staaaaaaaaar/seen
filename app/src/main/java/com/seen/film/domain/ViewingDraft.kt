package com.seen.film.domain

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Kept in a private file, never in the activity's saved-state Bundle. */
data class ViewingDraft(
    val key: String = UUID.randomUUID().toString(),
    val movieId: String = UUID.randomUUID().toString(),
    val isNewMovie: Boolean = true,
    val isEditing: Boolean = false,
    val title: String = "",
    val originalTitle: String = "",
    val year: String = "",
    val posterKey: String? = null,
    val tmdbId: Long? = null,
    val releaseDate: String? = null,
    val directorNames: String? = null,
    val runtimeMinutes: Int? = null,
    val productionCountries: String? = null,
    val date: String = LocalDate.now().toString(),
    val time: String = "",
    val location: String = "",
    val rating: Int? = null,
    val notes: String = "",
    val forWatchlist: Boolean = false,
)

object ViewingRules {
    fun validate(draft: ViewingDraft, today: LocalDate = LocalDate.now()): Map<String, String> = buildMap {
        if (draft.tmdbId != null && draft.tmdbId <= 0) put("title", "无效的 TMDB ID")
        if (draft.title.isBlank()) put("title", "请输入电影名称")
        else if (draft.title.codePointCount(0, draft.title.length) > 300) put("title", "片名最多 300 个字符")
        if (draft.originalTitle.codePointCount(0, draft.originalTitle.length) > 300) put("originalTitle", "原名最多 300 个字符")
        if (draft.year.isNotBlank() && (draft.year.toIntOrNull()?.let { it in 1800..9999 } != true)) put("year", "请输入 1800–9999 之间的年份")
        val date = runCatching { LocalDate.parse(draft.date) }.getOrNull()
        if (date == null) put("date", "请选择有效日期")
        else if (date > today) put("date", "观看日期不能晚于今天")
        if (draft.time.isNotBlank() && runCatching { LocalTime.parse(draft.time) }.isFailure) put("time", "请选择有效时间")
        if (draft.rating != null && draft.rating !in 1..10) put("rating", "评分为 0.5–5 星")
        if (draft.location.codePointCount(0, draft.location.length) > 200) put("location", "地点最多 200 个字符")
        if (draft.notes.codePointCount(0, draft.notes.length) > 50_000) put("notes", "感受最多 50,000 个字符")
    }.filterKeys { !draft.forWatchlist || it in setOf("title", "originalTitle", "year") }
    fun ratingLabel(value: Int?): String = value?.let { "%.1f".format(java.util.Locale.ROOT, it / 2.0) } ?: "未评分"
}
