package com.seen.film.data

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class BackupRulesTest {
    private val id = "3d111111-1111-4111-8111-111111111111"
    private val now = "2020-01-01T00:00:00Z"
    private val movie = Movie(id, "中文 🎬", createdAt = now, updatedAt = now)
    private val viewing = Viewing("7a222222-2222-4222-8222-222222222222", id, "2099-01-01", createdAt = now, updatedAt = now)
    private fun rejected(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun unicodeLimitsCountCodePointsAndRejectUnpairedSurrogates() {
        BackupRules.text("🎬".repeat(300), 300)
        rejected { BackupRules.text("🎬".repeat(301), 300) }
        rejected { BackupRules.text("\uD800", 300) }
        rejected { BackupRules.text("\uDC00", 300) }
        BackupRules.text("e\u0301", 2)
    }
    @Test fun nullMidnightMinimumRatingAndFutureDatesRemainDistinct() {
        BackupRules.movie(movie.copy(runtimeMinutes = 0, releaseYear = 1800, releaseDate = "2000-02-29"))
        BackupRules.viewing(viewing)
        BackupRules.viewing(viewing.copy(viewedTime = "00:00", ratingHalfStars = 1, notes = ""))
        rejected { BackupRules.viewing(viewing.copy(ratingHalfStars = 0)) }
        rejected { BackupRules.viewing(viewing.copy(viewedDate = "2023-02-29")) }
        rejected { BackupRules.movie(movie.copy(runtimeMinutes = -1)) }
        rejected { BackupRules.movie(movie.copy(tmdbId = 0)) }
    }
    @Test fun duplicatesAndDanglingReferencesAreRejectedButEmptyLibrariesAreValid() {
        BackupRules.relationships(emptyList(), emptyList(), emptySet())
        BackupRules.relationships(listOf(movie), emptyList(), emptySet())
        rejected { BackupRules.relationships(listOf(movie, movie), emptyList(), emptySet()) }
        rejected { BackupRules.relationships(listOf(movie), listOf(viewing, viewing), emptySet()) }
        rejected { BackupRules.relationships(emptyList(), listOf(viewing), emptySet()) }
        rejected { BackupRules.relationships(listOf(movie.copy(posterKey = id)), emptyList(), emptySet()) }
        rejected { BackupRules.relationships(listOf(movie), emptyList(), setOf(id)) }
        rejected { BackupRules.relationships(listOf(movie.copy(tmdbId = 1), movie.copy(id = viewing.id, tmdbId = 1)), emptyList(), emptySet()) }
    }
    @Test fun lexicalGuardRejectsRawControlsInvalidEscapesAndDeepUnknownFields() {
        fun read(s: String) { StrictJsonText(StringReader(s)).use { val b = CharArray(5); while (it.read(b) >= 0) Unit } }
        read("{\"x\":\"escaped\\n\\uD83C\\uDFAC\"}")
        rejected { read("{\"x\":\"raw\nnewline\"}") }
        rejected { read("{\"x\":\"\\x\"}") }
        rejected { read("[".repeat(17) + "]".repeat(17)) }
    }
    @Test fun idsVersionsAndLongNotesAreNotSilentlyNormalized() {
        BackupRules.uuid(id)
        rejected { BackupRules.uuid(id.uppercase()) }
        rejected { BackupRules.uuid("1-1-1-1-1") }
        BackupRules.viewing(viewing.copy(notes = "🎬".repeat(50000)))
        rejected { BackupRules.viewing(viewing.copy(notes = "🎬".repeat(50001))) }
        rejected { BackupRules.movie(movie.copy(title = " ")) }
    }
    @Test fun entityAndAssetCountLimitsRejectBeforeRelationships() {
        rejected { BackupRules.relationships(List(10001) { movie }, emptyList(), emptySet()) }
        rejected { BackupRules.relationships(listOf(movie), List(100001) { viewing }, emptySet()) }
        rejected { BackupRules.relationships(emptyList(), emptyList(), (0..10000).map { it.toString() }.toSet()) }
    }

}
