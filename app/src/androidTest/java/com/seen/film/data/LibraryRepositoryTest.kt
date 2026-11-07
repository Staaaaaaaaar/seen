package com.seen.film.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.seen.film.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LibraryRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun moviesRatingsNotesAndViewingsAreIndependent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SeenDatabase::class.java).build()
        try {
            val repo = SeenRepository(db)
            val id = repo.addMovie(ViewingDraft(title = "Independent", tmdbId = 42, date = ""))
            assertEquals(id, repo.addMovie(ViewingDraft(title = "Duplicate", tmdbId = 42)))
            assertTrue(db.dao().observeLibrary().first().single().viewings.isEmpty())
            repo.setPersonalRating(id, 9)
            val note = RecordDraft(movieId = id, kind = RecordKind.NOTE, date = "2020-01-02", body = "Not another viewing", location = "Home")
            repo.saveRecord(note)
            assertTrue(db.dao().observeLibrary().first().single().viewings.isEmpty())
            assertEquals(9, repo.movie(id)!!.personalRatingHalfStars)
            val viewing = RecordDraft(movieId = id, kind = RecordKind.VIEWING, date = "2020-01-01")
            repo.saveRecord(viewing)
            var history = db.dao().observeLibrary().first().single()
            assertEquals(1, history.viewings.size); assertNull(history.viewings.single().viewedTime)
            assertNull(history.viewings.single().ratingHalfStars)
            assertEquals("Home", history.notes.single().location)
            repo.saveRecord(viewing.copy(isEditing = true, time = "00:00"))
            assertEquals("00:00", db.dao().viewing(viewing.key)!!.viewedTime)
            assertEquals(9, repo.movie(id)!!.personalRatingHalfStars)
            repo.deleteRecord(RecordKind.VIEWING, viewing.key)
            history = db.dao().observeLibrary().first().single()
            assertTrue(history.viewings.isEmpty()); assertEquals(1, history.notes.size)
            assertEquals(9, history.movie.personalRatingHalfStars)
            repo.setPersonalRating(id, null); assertNull(repo.movie(id)!!.personalRatingHalfStars)
            assertTrue(runCatching { repo.setPersonalRating(id, 11) }.isFailure)
            db.dao().insertArchivedPosters(listOf(ArchivedPoster(id, "legacy.jpg")))
            db.dao().insertGenres(listOf(MovieGenre(id, 1, "Drama", 0)))
            repo.deleteMovie(id)
            assertTrue(db.dao().allNotes().isEmpty()); assertTrue(db.dao().allGenres().isEmpty())
            assertTrue(db.dao().allArchivedPosters().isEmpty()); assertNull(repo.movie(id))
        } finally { db.close() }
    }

    @Test fun failedSaveRetainsPrivateRecordDraftAndNoPartialViewing() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SeenDatabase::class.java).build()
        val folder = File(context.cacheDir, "record-draft-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir() = folder }
        try {
            val repo = SeenRepository(db)
            val id = repo.addMovie(ViewingDraft(title = "Draft"))
            val store = DraftStore(isolated)
            val note = RecordDraft(movieId = id, kind = RecordKind.NOTE, date = "2020-01-01", body = "Keep this", location = "Home")
            store.writeRecord(note)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER refuse_note BEFORE INSERT ON movie_notes BEGIN SELECT RAISE(ABORT,'fixture failure'); END")
            assertTrue(runCatching { repo.saveRecord(note) }.isFailure)
            assertEquals(note, store.readRecord()); assertTrue(db.dao().allViewings().isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_note")
            repo.saveRecord(note)
            assertEquals("Keep this", db.dao().allNotes().single().body)
            assertEquals(note, store.readRecord())
            store.clearRecord(); assertNull(store.readRecord())
        } finally { db.close(); folder.deleteRecursively() }
    }
    @Test fun oldDraftRetainsItsUnsubmittedRatingAndCorruptionIsNotOverwritten() = runBlocking {
        val folder = File(context.cacheDir, "legacy-record-draft-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir() = folder }
        val db = Room.inMemoryDatabaseBuilder(context, SeenDatabase::class.java).build()
        try {
            val store = DraftStore(isolated)
            val draft = ViewingDraft(title = "Old draft", date = "2020-01-01", rating = 7)
            store.write(draft)
            val file = File(folder, "viewing-draft.json")
            val old = org.json.JSONObject(file.readText()).put("schemaVersion", 1)
            old.remove("legacyRatingPending"); file.writeText(old.toString())
            val recovered = store.read()!!
            assertTrue(recovered.legacyRatingPending); assertEquals(7, recovered.rating)
            val repo = SeenRepository(db); val movieId = repo.save(recovered)
            assertEquals(7, repo.movie(movieId)!!.personalRatingHalfStars)
            old.put("forWatchlist", true); file.writeText(old.toString())
            assertFalse(store.read()!!.legacyRatingPending)
            val broken = "{invalid-draft"
            file.writeText(broken)
            assertTrue(runCatching { store.read() }.isFailure)
            assertEquals(broken, file.readText())
        } finally { db.close(); folder.deleteRecursively() }
    }

    @Test fun staleLocalizedDraftCannotEnterNewLanguageLibrary()=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(context,SeenDatabase::class.java).build()
        try{
            db.dao().putMetadataState(LibraryMetadataState(activeLanguage="en-US",generation=1))
            val result=runCatching{SeenRepository(db).addMovie(ViewingDraft(title="旧中文草稿",tmdbId=4242,movieOnly=true))}
            assertTrue("Old-language draft must keep its input rather than publish mixed metadata",result.isFailure)
            assertEquals(0,db.dao().movieCount())
        }finally{db.close()}
    }

}
