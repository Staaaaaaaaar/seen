package com.seen.film.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosterStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun importedPosterRemainsReadableAfterOriginalIsRemoved() = runBlocking {
        val source = File(context.cacheDir, "${UUID.randomUUID()}.png")
        val image = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.BLUE)
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val store = PosterStore(context)
        try {
            val key = store.import(Uri.fromFile(source))
            source.delete()
            assertTrue(store.file(key)!!.length() > 0)
            val decoded = android.graphics.BitmapFactory.decodeFile(store.file(key)!!.path)
            assertEquals(48, decoded.width)
            decoded.recycle()
            store.removeUnused(key, emptyList())
            // Reclamation is deferred so concurrent snapshots/drafts cannot lose media.
            assertNotNull(store.file(key))
        } finally { source.delete() }
    }

    @Test fun downloadedPosterIsPrivateAndReadableAfterStoreRecreation() = runBlocking {
        val image = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        image.recycle()
        val store = PosterStore(context)
        val key = store.importDownloaded(bytes.toByteArray())
        try {
            assertTrue(PosterStore(context).file(key)!!.length() > 0)
            assertTrue(runCatching { store.importDownloaded("broken".toByteArray()) }.isFailure)
            assertTrue(PosterStore(context).file(key)!!.isFile)
        } finally { store.removeUnused(key, emptyList()) }
    }

    @Test fun malformedImageDoesNotLeaveAPosterBehind() = runBlocking {
        val source = File(context.cacheDir, "${UUID.randomUUID()}.png").apply { writeText("not an image") }
        val directory = File(context.filesDir, "posters")
        val store = PosterStore(context)
        val before = directory.list().orEmpty().toSet()
        try {
            assertTrue(runCatching { store.import(Uri.fromFile(source)) }.isFailure)
            assertEquals(before, directory.list().orEmpty().toSet())
        } finally { source.delete() }
    }
}
