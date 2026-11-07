package com.seen.film.domain

import java.time.LocalDate
import java.util.UUID

enum class RecordKind { VIEWING, NOTE }

data class RecordDraft(
    val key: String = UUID.randomUUID().toString(),
    val movieId: String,
    val kind: RecordKind,
    val isEditing: Boolean = false,
    val date: String = LocalDate.now().toString(),
    val time: String = "",
    val location: String = "",
    val body: String = "",
)
