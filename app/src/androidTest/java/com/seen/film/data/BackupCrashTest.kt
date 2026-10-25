package com.seen.film.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.seen.film.domain.ViewingDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Run only through scripts/verify-backup-crash.py, which kills the process externally. */
class BackupCrashTest {
    private fun phase(): String? = InstrumentationRegistry.getArguments().getString("backupCrashPhase")
    private val base get() = ApplicationProvider.getApplicationContext<Context>()
    private val root get() = base.getDir("backup-crash-fixture", Context.MODE_PRIVATE)
    private fun context() = object : ContextWrapper(base) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, name)
    }
    @Test fun prepareAndWaitForExternalKill() = runBlocking {
        val phase = phase(); assumeTrue("External crash controller required", phase != null)
        root.deleteRecursively(); root.mkdirs()
        val context = context()
        val db = Room.databaseBuilder(context, SeenDatabase::class.java, "fixture.db").build()
        try {
            val access = LibraryAccess(); val posters = PosterStore(context); val drafts = DraftStore(context, access)
            val image = File(root, "poster.jpg")
            val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            val key = posters.importBackup(image)
            SeenRepository(db, access).save(ViewingDraft(title = "旧库合成电影", posterKey = key, date = "2000-01-01", notes = "旧感受🎬"))
            val old = db.dao().allMovies().single(); val oldViewing = db.dao().allViewings().single()
            val codec = BackupCodec()
            codec.write(File(root, "old.json"), BackupData("0.3.0", "2020-01-01T00:00:00Z", listOf(old.copy(posterKey = UUID.nameUUIDFromBytes(key.toByteArray()).toString())), listOf(oldViewing), mapOf(UUID.nameUUIDFromBytes(key.toByteArray()).toString() to posters.file(key)!!)))
            val newId = UUID.randomUUID().toString(); val asset = UUID.randomUUID().toString()
            val incoming = File(root, "new.json")
            codec.write(incoming, BackupData("0.3.0", "2020-01-01T00:00:00Z", listOf(old.copy(id = newId, title = "新库合成电影", posterKey = asset)), listOf(oldViewing.copy(movieId = newId, notes = "新感受🎬")), mapOf(asset to image)))
            val service = BackupRepository(context, db, posters, drafts, access, checkpoint = {
                if (it == phase) {
                    File(root, "ready").writeText(phase!!)
                    // External controller uses SIGKILL; no exception/finally can mimic this boundary.
                    while (true) delay(1000)
                }
            })
            service.restore(service.preview(Uri.fromFile(incoming)))
            fail("Controller did not terminate the process")
        } finally { db.close() }
    }
    @Test fun verifyAfterExternalKill() = runBlocking {
        val phase = phase(); assumeTrue("External crash controller required", phase != null)
        val context = context(); val db = Room.databaseBuilder(context, SeenDatabase::class.java, "fixture.db").build()
        try {
            val expectedFile = File(root, if (phase == "committed") "new.json" else "old.json")
            val expected = BackupCodec().read(expectedFile, root)
            val actual = db.dao().allMovies(); val viewings = db.dao().allViewings()
            assertEquals(expected.movies.map { it.copy(posterKey = null) }, actual.map { it.copy(posterKey = null) })
            assertEquals(expected.viewings, viewings)
            val posters = PosterStore(context)
            for (m in actual) assertEquals(expected.assets.getValue(expected.movies.single { it.id == m.id }.posterKey!!).sha256(), posters.file(m.posterKey)!!.sha256())
            assertNull(DraftStore(context).read())
        } finally { db.close(); root.deleteRecursively() }
    }
}
