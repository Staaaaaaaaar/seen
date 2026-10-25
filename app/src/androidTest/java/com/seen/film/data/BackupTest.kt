package com.seen.film.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seen.film.domain.ViewingDraft
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val now = "2020-01-01T00:00:00Z"
    private val movieId = "3d111111-1111-4111-8111-111111111111"
    private val viewId = "7a222222-2222-4222-8222-222222222222"
    private val assetId = "8a222222-2222-4222-8222-222222222222"
    private val movie = Movie(movieId, "中文 🎬 e\u0301", "", 2001, "2001-03-01", 123, assetId, "TMDB", "导演\nDirector", 0, "中国", now, now)
    private val viewing = Viewing(viewId, movieId, "2099-01-01", null, "", null, "长感受🎬\n".repeat(2000), now, now)
    private inner class Fixture : Closeable {
        val parent = ApplicationProvider.getApplicationContext<Context>()
        val root = File(parent.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(parent) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val name = "backup-test-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, SeenDatabase::class.java, name).build()
        val access = LibraryAccess()
        val posters = PosterStore(context)
        val drafts = DraftStore(context, access)
        fun service(checkpoint: suspend (String) -> Unit = {}) = BackupRepository(context, db, posters, drafts, access, checkpoint = checkpoint)
        fun asset(): File {
            val file = File(root, "fixture.jpg")
            val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            return file
        }
        fun document(withPoster: Boolean = true): File {
            val file = File(root, "source.json")
            BackupCodec().write(file, BackupData("0.3.0", now,
                listOf(if (withPoster) movie else movie.copy(posterKey = null), movie.copy(id = assetId, title = "零观看电影", tmdbId = null, posterKey = null)),
                listOf(viewing, viewing.copy(id = assetId, viewedTime = "00:00", ratingHalfStars = 1, notes = null)),
                if (withPoster) mapOf(assetId to asset()) else emptyMap()))
            return file
        }
        suspend fun original(): Pair<List<Movie>, List<Viewing>> {
            val key = posters.importBackup(asset())
            val m = movie.copy(id = UUID.randomUUID().toString(), posterKey = key, tmdbId = 987, title = "原库")
            db.dao().insertMovie(m)
            db.dao().insertViewing(viewing.copy(id = UUID.randomUUID().toString(), movieId = m.id))
            return snapshot()
        }
        suspend fun snapshot() = db.dao().allMovies().sortedBy { it.id } to db.dao().allViewings().sortedBy { it.id }
        override fun close() { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    }
    private suspend fun rejected(block: suspend () -> Unit) { assertTrue("Expected rejection", runCatching { block() }.isFailure) }

    @Test fun allFieldsIdsZeroViewingsAndPosterBytesRoundTripAcrossReopen() = runBlocking {
        Fixture().use { f ->
            val service = f.service(); val source = f.document(); val digest = f.asset().sha256()
            val preview = service.preview(Uri.fromFile(source))
            assertEquals(2, preview.movieCount); assertEquals(2, preview.viewingCount); assertTrue(preview.hasFutureDates)
            service.restore(preview)
            val restored = f.db.dao().movie(movieId)!!
            assertEquals(movie.copy(posterKey = restored.posterKey), restored)
            assertEquals(digest, f.posters.file(restored.posterKey)!!.sha256())
            assertEquals(viewing, f.db.dao().viewing(viewId))
            assertEquals("00:00", f.db.dao().viewing(assetId)!!.viewedTime)
            val prepared = service.prepareExport()
            val destination = File(f.root, "exported.json"); service.finishExport(prepared, Uri.fromFile(destination))
            val read = BackupCodec().read(destination, f.root)
            assertEquals(2, read.movies.size); assertEquals(2, read.viewings.size); assertEquals(1, read.assets.size)
            val before = f.snapshot()
            f.db.close(); f.db = Room.databaseBuilder(f.context, SeenDatabase::class.java, f.name).build()
            assertEquals(before, f.snapshot())
            val next = f.service(); next.restore(next.preview(Uri.fromFile(destination)))
            assertEquals(before.first.map { it.copy(posterKey = null) }, f.snapshot().first.map { it.copy(posterKey = null) })
            assertEquals(before.second, f.snapshot().second)
        }
    }
    @Test fun malformedDocumentsNeverChangeDatabaseDraftOrOldMedia() = runBlocking {
        Fixture().use { f ->
            val original = f.original(); val oldPoster = f.posters.file(original.first.single().posterKey)!!; val oldHash = oldPoster.sha256()
            val service = f.service(); val valid = f.document(false).readText()
            val bad = listOf(
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                valid.replace("\"runtimeMinutes\":0", "\"runtimeMinutes\":2147483648"),
                valid.replace("\"tmdbId\":123", "\"tmdbId\":9223372036854775808"),
                valid.replace("\"tmdbId\":123", "\"tmdbId\":-1"),
                valid.replace("\"ratingHalfStars\":1", "\"ratingHalfStars\":0"),
                valid.replace("\"movieId\":\"$movieId\"", "\"movieId\":\"$viewId\""),
                valid.replace("\"id\":\"$assetId\"", "\"id\":\"$movieId\""),
                valid.replace("\"originalTitle\":\"\",", ""),
                valid.replace("\"originalTitle\":\"\"", "\"originalTitle\":true"),
                valid.replace("\"originalTitle\":\"\"", "\"originalTitle\":\"\\ud800\""),
                valid.replace("\"originalTitle\":\"\"", "\"originalTitle\":\"bad\ncontrol\""),
                valid.dropLast(2), valid + "{}", "[]",
                valid.dropLast(1) + ",\"unknown\":{\"x\":1,\"x\":2}}",
                valid.replace("\"movies\":[", "\"movies\":null,\"ignored\":["),
                valid.dropLast(1) + ",\"unknown\":" + "[".repeat(17) + "0" + "]".repeat(17) + "}",
            )
            for ((index, text) in bad.withIndex()) {
                val file = File(f.root, "bad-$index.json").apply { writeText(text) }
                rejected { service.preview(Uri.fromFile(file)) }
                assertEquals("case $index", original, f.snapshot()); assertEquals(oldHash, oldPoster.sha256())
            }
            val invalidUtf8 = File(f.root, "bad-utf8.json").apply { writeBytes(byteArrayOf(0xC3.toByte(), 0x28)) }
            rejected { service.preview(Uri.fromFile(invalidUtf8)) }
            val p = service.preview(Uri.fromFile(f.document(false))); service.cancel(p)
        }
    }
    @Test fun invalidMediaHashBase64JpegSizeAndReferencesAreRejected() = runBlocking {
        Fixture().use { f ->
            val original = f.original(); val text = f.document().readText()
            val mutations: List<(JSONObject) -> Unit> = listOf(
                { it.getJSONArray("assets").getJSONObject(0).put("sha256", "0".repeat(64)) },
                { it.getJSONArray("assets").getJSONObject(0).put("base64", "@@@=") },
                { it.getJSONArray("assets").getJSONObject(0).put("mimeType", "image/png") },
                { it.getJSONArray("assets").getJSONObject(0).put("base64", "A".repeat(2796208)) },
                { it.getJSONArray("movies").getJSONObject(0).put("posterAssetId", viewId) },
                { it.getJSONArray("movies").getJSONObject(0).put("posterAssetId", JSONObject.NULL) },
                { it.getJSONArray("assets").put(it.getJSONArray("assets").getJSONObject(0)) },
            )
            for (mutate in mutations) {
                val j = JSONObject(text); mutate(j); val file = File(f.root, "bad.json").apply { writeText(j.toString()) }
                rejected { f.service().preview(Uri.fromFile(file)) }; assertEquals(original, f.snapshot())
            }
            val badImage = File(f.root, "not.jpg").apply { writeText("not JPEG") }
            rejected { BackupCodec().validateImage(badImage) }
            val huge = File(f.root, "huge.json"); java.io.RandomAccessFile(huge, "rw").use { it.setLength(BackupRules.MAX_BYTES + 1) }
            rejected { f.service().preview(Uri.fromFile(huge)) }; assertEquals(original, f.snapshot())
        }
    }
    @Test fun previewLocksWritesAndDraftsAndCancelReleasesWithoutChanges() = runBlocking {
        Fixture().use { f ->
            val original = f.original(); val service = f.service(); val document = f.document(false)
            val draft = ViewingDraft(title = "不能丢弃")
            f.drafts.write(draft); rejected { service.preview(Uri.fromFile(document)) }; assertEquals(draft, f.drafts.read())
            f.drafts.clear(); val p = service.preview(Uri.fromFile(document))
            rejected { f.drafts.write(draft) }
            rejected { SeenRepository(f.db, f.access).save(draft) }
            rejected { SeenRepository(f.db, f.access).deleteViewing(original.second.single().id) }
            val export = service.prepareExport(); service.cancelExport(export)
            assertEquals(original, f.snapshot()); service.cancel(p)
            f.drafts.write(draft); assertEquals(draft, f.drafts.read())
        }
    }
    @Test fun mediaAndSqlFailuresRollbackAndCanRetry() = runBlocking {
        for (phase in listOf("media-ready", "sql-deleted", "sql-movies")) Fixture().use { f ->
            val original = f.original(); val old = f.posters.file(original.first.single().posterKey)!!; val hash = old.sha256()
            var fail = true
            val service = f.service { if (it == phase && fail) throw IOException("injected $phase") }
            val p = service.preview(Uri.fromFile(f.document()))
            rejected { service.restore(p) }; assertEquals(original, f.snapshot()); assertEquals(hash, old.sha256())
            assertEquals(1, File(f.context.filesDir, "posters").listFiles()!!.size)
            fail = false; service.restore(p); assertNotNull(f.db.dao().movie(movieId)); assertEquals(hash, old.sha256())
        }
        Fixture().use { f ->
            val original = f.original(); val old = f.posters.file(original.first.single().posterKey)!!; val hash = old.sha256()
            val broken = PosterStore(f.context, backupCheckpoint = { throw IOException("partial media") })
            val service = BackupRepository(f.context, f.db, broken, f.drafts, f.access)
            val p = service.preview(Uri.fromFile(f.document())); rejected { service.restore(p) }
            assertEquals(original, f.snapshot()); assertEquals(hash, old.sha256())
            assertEquals(1, File(f.context.filesDir, "posters").listFiles()!!.size); service.cancel(p)
        }
    }
    @Test fun modifiedPrivatePreviewFailsAndEmptyBackupRequiresExplicitCommit() = runBlocking {
        Fixture().use { f ->
            val original = f.original(); val service = f.service()
            val p = service.preview(Uri.fromFile(f.document())); p.data.assets.values.single().appendText("tampered")
            rejected { service.restore(p) }; assertEquals(original, f.snapshot()); service.cancel(p)
            val empty = File(f.root, "empty.json")
            BackupCodec().write(empty, BackupData("0.3.0", now, emptyList(), emptyList(), emptyMap()))
            val preview = service.preview(Uri.fromFile(empty)); assertEquals(0, preview.movieCount); assertEquals(original, f.snapshot())
            service.restore(preview); assertTrue(f.snapshot().first.isEmpty()); assertTrue(f.snapshot().second.isEmpty())
            val prepared = service.prepareExport(); service.cancelExport(prepared)
        }
    }
    @Test fun exportRejectsMissingMediaAndDestinationWriteFlushOrCloseFailure() = runBlocking {
        Fixture().use { f ->
            val original = f.original()
            for (phase in listOf("write", "flush", "close")) {
                val service = BackupRepository(f.context, f.db, f.posters, f.drafts, f.access, openOutput = {
                    object : OutputStream() {
                        override fun write(b: Int) { if (phase == "write") throw IOException("write") }
                        override fun flush() { if (phase == "flush") throw IOException("flush") }
                        override fun close() { if (phase == "close") throw IOException("close") }
                    }
                })
                val prepared = service.prepareExport(); rejected { service.finishExport(prepared, Uri.parse("content://invalid/new")) }
                assertEquals(original, f.snapshot()); assertFalse(prepared.directory.exists())
            }
            f.posters.file(original.first.single().posterKey)!!.delete()
            rejected { f.service().prepareExport() }; assertEquals(original, f.snapshot())
        }
    }
    @Test fun contentProviderRoundTripReadDenialAndWriteDenialPreserveOriginal() = runBlocking {
        Fixture().use { f ->
            val original = f.original(); val service = f.service()
            val document = Uri.parse("content://com.seen.film.debug.test.backups/document")
            service.finishExport(service.prepareExport(), document)
            val preview = service.preview(document)
            assertEquals(original.first.size, preview.movieCount); service.cancel(preview)
            val denied = Uri.parse("content://com.seen.film.debug.test.backups/denied")
            rejected { service.preview(denied) }
            // Recreate after denied read: the synthetic provider may delete on failed export cleanup.
            rejected { service.finishExport(service.prepareExport(), denied) }
            assertEquals(original, f.snapshot())
            f.context.contentResolver.delete(document, null, null); Unit
        }
    }

    @Test fun oversizedAndTruncatedJpegAndOversizedDimensionsAreRejected() = runBlocking {
        Fixture().use { f ->
            val oversized = File(f.root, "oversized.jpg")
            java.io.RandomAccessFile(oversized, "rw").use { it.setLength(BackupRules.MAX_IMAGE.toLong() + 1) }
            rejected { BackupCodec().validateImage(oversized) }
            val truncated = f.asset(); java.io.RandomAccessFile(truncated, "rw").use { it.setLength(it.length() - 2) }
            rejected { BackupCodec().validateImage(truncated) }
            val bitmap = Bitmap.createBitmap(2049, 1, Bitmap.Config.ARGB_8888)
            val dimensions = File(f.root, "dimensions.jpg")
            dimensions.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            rejected { BackupCodec().validateImage(dimensions) }
        }
    }

}
