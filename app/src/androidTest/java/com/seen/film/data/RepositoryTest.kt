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

    @Test fun deletingMovieIsAtomicPreservesOtherMoviesAndAllowsTmdbReimport() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SeenDatabase::class.java).build()
        try {
            val access = LibraryAccess()
            val repository = SeenRepository(db, access)
            val draft = ViewingDraft(title = "待删除电影", tmdbId = 42, date = "2020-01-01", notes = "第一次")
            val id = repository.save(draft)
            repository.save(draft.copy(key = UUID.randomUUID().toString(), isNewMovie = false, notes = "第二次"))
            val other = repository.save(ViewingDraft(title = "保留电影", date = "2021-01-01"))
            val before = db.dao().allViewings()
            val reservation = access.reserve { }
            assertTrue(runCatching { repository.deleteMovie(id) }.isFailure)
            assertEquals(before, db.dao().allViewings())
            access.release(reservation)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER refuse_delete BEFORE DELETE ON movies BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
            assertTrue(runCatching { repository.deleteMovie(id) }.isFailure)
            assertNotNull(repository.movie(id))
            assertEquals(before, db.dao().allViewings())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_delete")
            repository.deleteMovie(id)
            assertNull(repository.movie(id))
            assertEquals(before.filter { it.movieId == other }, db.dao().allViewings())
            assertNotNull(repository.movie(other))
            val replacement = repository.save(ViewingDraft(title = "重新导入", tmdbId = 42, date = "2022-01-01"))
            assertNotEquals(id, replacement)
            assertEquals(42L, repository.movie(replacement)!!.tmdbId)
        } finally { db.close() }
    }

    @Test fun opensHistoricalV1SchemaWithoutLosingRecords() = runBlocking {
        val name = "historical-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val schema = org.json.JSONObject(testContext.assets.open("com.seen.film.data.SeenDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val old = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (n in 0 until entities.length()) {
                val entity = entities.getJSONObject(n)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (n in 0 until setup.length()) old.execSQL(setup.getString(n))
            old.execSQL("INSERT INTO movies (id,title,source,createdAt,updatedAt) VALUES ('old-movie','旧版记录','MANUAL','2000','2000')")
            old.execSQL("INSERT INTO viewings (id,movieId,viewedDate,createdAt,updatedAt) VALUES ('old-viewing','old-movie','2000-01-01','2000','2000')")
            old.version = 1
        } finally { old.close() }
        val db = Room.databaseBuilder(context, SeenDatabase::class.java, name).build()
        try {
            val history = db.dao().observeLibrary().first().single()
            assertEquals("旧版记录", history.movie.title)
            assertEquals("old-viewing", history.viewings.single().id)
            assertNull(history.viewings.single().ratingHalfStars)
            SeenRepository(db).save(ViewingDraft(title = "新电影", tmdbId = 42, date = "2000-01-01"))
            assertEquals(2, db.dao().observeLibrary().first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun tmdbReusePreservesManualRecordsAndSurvivesReopen() = runBlocking {
        val name = "tmdb-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, SeenDatabase::class.java, name).build()
        try {
            val manual = ViewingDraft(title = "旧版手动电影", date = "2000-01-01")
            SeenRepository(db).save(manual)
            val online = ViewingDraft(title = "中文片名", originalTitle = "Original", year = "2001",
                date = "2002-01-01", tmdbId = 123, releaseDate = "2001-06-01",
                directorNames = "导演", runtimeMinutes = 120, productionCountries = "China")
            val id = SeenRepository(db).save(online)
            val repeat = online.copy(key = UUID.randomUUID().toString(), movieId = UUID.randomUUID().toString())
            assertEquals(id, SeenRepository(db).save(repeat))
            SeenRepository(db).save(repeat)
            db.close()
            db = Room.databaseBuilder(context, SeenDatabase::class.java, name).build()
            val library = db.dao().observeLibrary().first()
            assertEquals(2, library.size)
            val movie = library.single { it.movie.tmdbId == 123L }
            assertEquals(2, movie.viewings.size)
            assertEquals("导演", movie.movie.directorNames)
            assertEquals("TMDB", movie.movie.source)
            assertNotNull(db.dao().movie(manual.movieId))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun tmdbParsingAndDraftMetadataRoundTrip() = runBlocking {
        val parsed = TmdbRepository.parse(org.json.JSONObject("""{"id":42,"title":"中文","original_title":"Original","release_date":"","poster_path":null,"runtime":0,"credits":{"crew":[{"job":"Director","name":"导演"}]}}"""))
        assertNull(parsed.releaseDate)
        assertNull(parsed.posterPath)
        assertNull(parsed.runtime)
        assertEquals("导演", parsed.directors)
        val folder = File(context.cacheDir, "tmdb-draft-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir() = folder }
        try {
            val store = DraftStore(isolated)
            val draft = ViewingDraft(title = "资料", tmdbId = 42, releaseDate = "2020-01-01", directorNames = "导演", runtimeMinutes = 90, productionCountries = "中国")
            store.write(draft)
            assertEquals(draft, store.read())
            val json = org.json.JSONObject(File(folder, "viewing-draft.json").readText())
            listOf("tmdbId", "releaseDate", "directorNames", "runtimeMinutes", "productionCountries").forEach { json.remove(it) }
            File(folder, "viewing-draft.json").writeText(json.toString())
            assertNull(store.read()!!.tmdbId)
        } finally { folder.deleteRecursively() }
        assertTrue(runCatching { TmdbRepository(token = { "" }).search("电影") }.isFailure)
    }

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
