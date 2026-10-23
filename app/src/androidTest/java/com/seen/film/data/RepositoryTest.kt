package com.seen.film.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seen.film.domain.ViewingDraft
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun repeatedSaveIsIdempotentRepeatViewingIsIndependentAndDeletePreservesMovie() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SeenDatabase::class.java).build()
        try {
            val repository = SeenRepository(db)
            val first = ViewingDraft(title = "重复观看", date = "2020-02-29", notes = "中文、emoji 🎬\n第二行")
            repository.save(first)
            repository.save(first) // Retrying a committed operation never duplicates it.
            assertEquals(1, db.dao().observeLibrary().first().single().viewings.size)
            val again = first.copy(key = UUID.randomUUID().toString(), isNewMovie = false, notes = "", date = "2021-03-01")
            repository.save(again)
            assertEquals(2, db.dao().observeLibrary().first().single().viewings.size)
            repository.deleteViewing(first.key)
            assertEquals(again.key, db.dao().observeLibrary().first().single().viewings.single().id)
            repository.deleteViewing(again.key)
            assertNotNull(db.dao().movie(first.movieId))
            assertTrue(db.dao().observeLibrary().first().single().viewings.isEmpty())
        } finally { db.close() }
    }

    @Test fun failedEditRollsBackTheNewMovieAndSurvivesDatabaseReopen() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, SeenDatabase::class.java, name).build()
        val value = ViewingDraft(title = "保留记录", date = "2000-01-01", time = "", rating = null)
        try {
            SeenRepository(db).save(value)
            val failed = ViewingDraft(title = "不应留下孤立电影", date = "2000-01-01", isEditing = true)
            assertTrue(runCatching { SeenRepository(db).save(failed) }.isFailure)
            assertNull(db.dao().movie(failed.movieId))
            db.close()
            db = Room.databaseBuilder(context, SeenDatabase::class.java, name).build()
            assertEquals("保留记录", db.dao().observeLibrary().first().single().movie.title)
            assertNull(db.dao().viewing(value.key)!!.viewedTime)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun editingOneViewingPreservesTheOtherAndOriginalCreationTime() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SeenDatabase::class.java).build()
        try {
            val repository = SeenRepository(db)
            val first = ViewingDraft(title = "电影", date = "2020-01-01", notes = "第一次")
            repository.save(first)
            val createdAt = db.dao().viewing(first.key)!!.createdAt
            val second = first.copy(key = UUID.randomUUID().toString(), isNewMovie = false, notes = "第二次")
            repository.save(second)
            repository.save(first.copy(isNewMovie = false, isEditing = true, rating = 9, notes = "已编辑"))
            assertEquals(9, db.dao().viewing(first.key)!!.ratingHalfStars)
            assertEquals(createdAt, db.dao().viewing(first.key)!!.createdAt)
            assertEquals("第二次", db.dao().viewing(second.key)!!.notes)
        } finally { db.close() }
    }

    @Test fun privateDraftRoundTripPreservesNullAndLongUnicodeText() = runBlocking {
        // Use a dedicated context/files directory, never the application's real draft.
        val folder = File(context.cacheDir, "draft-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        try {
            val store = DraftStore(isolated)
            val value = ViewingDraft(title = "草稿", notes = "🎬 中文\n".repeat(1000))
            store.write(value)
            assertEquals(value, store.read())
            store.clear()
            assertNull(store.read())
        } finally { folder.deleteRecursively() }
    }
}
