package com.seen.film.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LibraryMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun upgradesV2WithoutInheritingAnEarlierRating() = verifyUpgrade(2)
    @Test fun upgradesV1ThroughTheWholeChain() = verifyUpgrade(1)

    private fun verifyUpgrade(version: Int) = runBlocking {
        val name = "library-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val schema = JSONObject(testContext.assets.open("com.seen.film.data.SeenDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val old = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO movies (id,title,posterKey,source,createdAt,updatedAt) VALUES ('one','Old movie','11111111-1111-4111-8111-111111111111.jpg','MANUAL','2020-01-01T00:00:00Z','2020-01-01T00:00:00Z')")
            old.execSQL("INSERT INTO movies (id,title,source,createdAt,updatedAt) VALUES ('two','Second movie','MANUAL','2020-01-01T00:00:00Z','2020-01-01T00:00:00Z')")
            if (version == 2) old.execSQL("UPDATE movies SET wantToWatch=1 WHERE id='one'")
            old.execSQL("INSERT INTO viewings (id,movieId,viewedDate,ratingHalfStars,notes,createdAt,updatedAt) VALUES ('a','one','2020-01-01',8,'Old note','2020-01-01T00:00:00Z','2020-01-01T00:00:00Z')")
            old.execSQL("INSERT INTO viewings (id,movieId,viewedDate,ratingHalfStars,createdAt,updatedAt) VALUES ('b','one','2021-01-01',null,'2021-01-01T00:00:00Z','2021-01-01T00:00:00Z')")
            old.execSQL("INSERT INTO viewings (id,movieId,viewedDate,ratingHalfStars,createdAt,updatedAt) VALUES ('c','two','2021-01-01',7,'2021-01-01T00:00:00Z','2021-01-01T00:00:00Z')")
            old.version = version
        } finally { old.close() }
        val db = Room.databaseBuilder(context, SeenDatabase::class.java, name)
            .addMigrations(*SeenDatabase.MIGRATIONS).build()
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals("The upgraded library must use schema v3", 3, sql.version)
            sql.query("SELECT personalRatingHalfStars,createdAt,wantToWatch FROM movies WHERE id='one'").use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0))
                assertEquals("2020-01-01T00:00:00Z", it.getString(1)); assertEquals(if (version == 2) 1 else 0, it.getInt(2))
            }
            sql.query("SELECT personalRatingHalfStars FROM movies WHERE id='two'").use { assertTrue(it.moveToFirst()); assertEquals(7, it.getInt(0)) }
            sql.query("SELECT ratingHalfStars,notes FROM viewings WHERE id='a'").use { assertTrue(it.moveToFirst()); assertEquals(8, it.getInt(0)); assertEquals("Old note", it.getString(1)) }
            sql.query("SELECT COUNT(*) FROM viewings").use { assertTrue(it.moveToFirst()); assertEquals(3, it.getInt(0)) }
            sql.query("SELECT posterKey FROM archived_posters WHERE movieId='one'").use { assertTrue(it.moveToFirst()); assertEquals("11111111-1111-4111-8111-111111111111.jpg", it.getString(0)) }
            for (table in listOf("movie_notes", "movie_genres", "movie_credits")) {
                sql.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
