package com.seen.film.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecordRulesTest {
    private val today = LocalDate.of(2026, 11, 7)
    private fun draft(kind: RecordKind = RecordKind.VIEWING) = RecordDraft(movieId = "movie", kind = kind, date = "2026-11-07")

    @Test fun emptyViewingCountsButEmptyNoteIsInvalid() {
        assertTrue(RecordRules.validate(draft(), today).isEmpty())
        assertTrue(RecordRules.validate(draft(RecordKind.NOTE).copy(body = "  \n"), today).containsKey("body"))
        assertTrue(RecordRules.validate(draft(RecordKind.NOTE).copy(body = "A thought"), today).isEmpty())
    }
    @Test fun datesTimesAndMissingTimeRemainDifferent() {
        assertTrue(RecordRules.validate(draft().copy(time = ""), today).isEmpty())
        assertTrue(RecordRules.validate(draft().copy(time = "00:00"), today).isEmpty())
        assertTrue(RecordRules.validate(draft().copy(time = "25:00"), today).containsKey("time"))
        assertTrue(RecordRules.validate(draft().copy(date = "2026-11-08"), today).containsKey("date"))
        assertTrue(RecordRules.validate(draft().copy(date = "2026-02-30"), today).containsKey("date"))
    }
    @Test fun unicodeLimitsDoNotCountAnEmojiTwice() {
        assertFalse(RecordRules.validate(draft().copy(location = "🎬".repeat(200)), today).containsKey("location"))
        assertTrue(RecordRules.validate(draft().copy(location = "🎬".repeat(201)), today).containsKey("location"))
        assertFalse(RecordRules.validate(draft().copy(body = "🎬".repeat(50_000)), today).containsKey("body"))
        assertTrue(RecordRules.validate(draft().copy(body = "🎬".repeat(50_001)), today).containsKey("body"))
    }
}
