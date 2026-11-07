package com.seen.film.domain

import java.time.LocalDate
import java.time.LocalTime

object RecordRules {
    fun validate(draft: RecordDraft, today: LocalDate = LocalDate.now()): Map<String, String> = buildMap {
        if (draft.movieId.isBlank()) put("movieId", "请选择电影")
        val date = runCatching { LocalDate.parse(draft.date) }.getOrNull()
        if (date == null) put("date", "请选择有效日期")
        else if (date > today) put("date", "日期不能晚于今天")
        if (draft.time.isNotBlank() && runCatching { LocalTime.parse(draft.time) }.isFailure) put("time", "请选择有效时间")
        if (draft.kind == RecordKind.NOTE && draft.body.isBlank()) put("body", "请写一点想法")
        if (draft.body.codePointCount(0, draft.body.length) > 50_000) put("body", "正文最多 50,000 个字符")
        if (draft.location.codePointCount(0, draft.location.length) > 200) put("location", "地点最多 200 个字符")
    }
}
