package com.seen.film.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.seen.film.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LibraryBackupTest {
    @Test fun exportsAndRestoresIndependentRatingsNotesAndMetadata() = runBlocking {
        val parent = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(parent.cacheDir, "library-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(parent) {
            override fun getFilesDir() = File(folder, "files").apply { mkdirs() }
            override fun getCacheDir() = File(folder, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, SeenDatabase::class.java).build()
        try {
            val access = LibraryAccess(); val drafts = DraftStore(context, access)
            val repo = SeenRepository(db, access)
            val id = repo.addMovie(ViewingDraft(title = "Backup movie"))
            repo.setPersonalRating(id, 9)
            val note = RecordDraft(movieId = id, kind = RecordKind.NOTE, date = "2020-01-01", body = "A note", location = "Home")
            repo.saveRecord(note)
            db.dao().insertGenres(listOf(MovieGenre(id, 878, "科幻", 0)))
            db.dao().insertCredits(listOf(MovieCredit(id, "credit-1", 12, "演员", true, character = "角色", position = 0)))
            val service = BackupRepository(context, db, PosterStore(context), drafts, access)
            val prepared = service.prepareExport()
            val json = JSONObject(prepared.file.readText())
            assertEquals(2, json.getInt("schemaVersion"))
            assertEquals(9, json.getJSONArray("movies").getJSONObject(0).getInt("personalRatingHalfStars"))
            assertEquals(1, json.getJSONArray("notes").length())
            val file = File(folder, "export.json")
            service.finishExport(prepared, Uri.fromFile(file))
            repo.setPersonalRating(id, null)
            service.restore(service.preview(Uri.fromFile(file)))
            assertEquals(9, db.dao().movie(id)!!.personalRatingHalfStars)
            assertEquals("A note", db.dao().allNotes().single().body)
            assertNull(db.dao().allNotes().single().recordedTime)
            assertEquals("Home", db.dao().allNotes().single().location)
            assertEquals(878L, db.dao().allGenres().single().genreId)
            assertEquals("角色", db.dao().allCredits().single().character)
            assertTrue(db.dao().allViewings().isEmpty())
            drafts.writeRecord(note)
            assertTrue(runCatching { service.preview(Uri.fromFile(file)) }.isFailure)
        } finally { db.close(); folder.deleteRecursively() }
    }

    @Test fun oldBackupUsesLatestViewingRatingIncludingNull() {
        val parent = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(parent.cacheDir, "old-library-backup-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val now = "2020-01-01T00:00:00Z"
            val a = Movie(UUID.randomUUID().toString(), "First", createdAt = now, updatedAt = now)
            val b = a.copy(id = UUID.randomUUID().toString(), title = "Second")
            val first = Viewing(UUID.randomUUID().toString(), a.id, "2020-01-01", ratingHalfStars = 8, createdAt = now, updatedAt = now)
            val latest = first.copy(id = UUID.randomUUID().toString(), viewedDate = "2020-01-02", ratingHalfStars = null)
            val other = first.copy(id = UUID.randomUUID().toString(), movieId = b.id, ratingHalfStars = 7)
            val file = File(folder, "legacy.json")
            BackupCodec().write(file, BackupData("1.1.0", now, listOf(a,b), listOf(first,latest,other), emptyMap()))
            val json = JSONObject(file.readText()).put("schemaVersion", 1)
            for (key in listOf("notes","genres","credits","archivedPosters")) json.remove(key)
            for (i in 0..1) for (key in listOf("personalRatingHalfStars","overview","metadataLanguage")) json.getJSONArray("movies").getJSONObject(i).remove(key)
            file.writeText(json.toString())
            val restored = BackupCodec().read(file, folder)
            assertNull(restored.movies.first { it.id == a.id }.personalRatingHalfStars)
            assertEquals(7, restored.movies.first { it.id == b.id }.personalRatingHalfStars)
            assertEquals(8, restored.viewings.first { it.id == first.id }.ratingHalfStars)
        } finally { folder.deleteRecursively() }
    }
}
