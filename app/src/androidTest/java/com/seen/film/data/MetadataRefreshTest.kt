package com.seen.film.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.seen.film.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MetadataRefreshTest {
    private class Fixture : Closeable {
        val parent = ApplicationProvider.getApplicationContext<Context>()
        val root = File(parent.cacheDir,"metadata-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(parent) {
            override fun getFilesDir() = File(root,"files").apply { mkdirs() }
            override fun getCacheDir() = File(root,"cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context,SeenDatabase::class.java).build()
        val access = LibraryAccess(); val posters = PosterStore(context)
        val drafts = DraftStore(context,access); val repo = SeenRepository(db,access)
        val ids = mutableListOf<String>()
        var oldPoster = ""
        val jpeg: ByteArray get() {
            val bitmap=Bitmap.createBitmap(20,30,Bitmap.Config.ARGB_8888)
            return java.io.ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,88,it); bitmap.recycle(); it.toByteArray() }
        }
        suspend fun seed() {
            oldPoster=posters.importDownloaded(jpeg)
            for (n in 1..2) ids += repo.addMovie(ViewingDraft(title="Old $n",tmdbId=n.toLong(),posterKey=oldPoster))
            db.dao().putMetadataState(LibraryMetadataState(activeLanguage="zh-CN",generation=5))
            db.dao().insertArchivedPosters(listOf(ArchivedPoster(ids[0],oldPoster)))
        }
        fun remote(failure: String?=null, entered: CountDownLatch?=null, release: CountDownLatch?=null, noPoster: Boolean=false) = TmdbRepository(token={"fixture"},fetch={url,_,_->
            if (url.contains("image.tmdb.org")) {
                if (url.endsWith("/2.jpg")) {
                    entered?.countDown(); check(release?.await(10,TimeUnit.SECONDS) != false)
                    when(failure) { "network" -> throw SocketTimeoutException("fixture timeout"); "auth" -> error("fixture unauthorized"); "image" -> return@TmdbRepository byteArrayOf(1,2,3) }
                }
                jpeg
            } else {
                val n=if(url.contains("/movie/2?"))2 else 1
                val poster=if(noPoster)"null" else "\"/$n.jpg\""
                """{"id":$n,"title":"English $n","original_title":"Original $n","poster_path":$poster,"overview":"Overview","genres":[{"id":878,"name":"Science Fiction"}],"credits":{"cast":[],"crew":[]}}""".toByteArray()
            }
        })
        fun coordinator(tmdb: TmdbRepository, checkpoint: suspend (String)->Unit = {}) = MetadataRefreshCoordinator(context,db,posters,drafts,access,tmdb,checkpoint)
        suspend fun assertOld() {
            assertEquals(listOf("Old 1","Old 2"),db.dao().allMovies().map { it.title })
            assertTrue(db.dao().allMovies().all { it.posterKey==oldPoster })
            assertEquals("zh-CN",db.dao().metadataState()!!.activeLanguage)
            assertEquals(5L,db.dao().metadataState()!!.generation)
        }
        override fun close(){db.close();root.deleteRecursively()}
    }
    @Test fun failedNetworkAuthenticationOrImagePreservesWholeOldVersion()=runBlocking {
        for(reason in listOf("network","auth","image")) Fixture().use { f->
            f.seed(); val c=f.coordinator(f.remote(failure=reason));c.refresh(MetadataLanguage.EN_US)
            assertTrue(c.state.value is MetadataRefreshState.Failed);f.assertOld()
            assertTrue(f.posters.file(f.oldPoster)!!.isFile)
        }
    }
    @Test fun failedPreparationBeforePublishNeverAdvancesLanguage()=runBlocking {
        Fixture().use { f->
            f.seed();val c=f.coordinator(f.remote(),checkpoint={if(it=="media-ready")throw java.io.IOException("fixture full disk")})
            c.refresh(MetadataLanguage.EN_US);assertTrue(c.state.value is MetadataRefreshState.Failed);f.assertOld()
        }
    }
    @Test fun preparationAllowsPersonalWritesAndExportButBlocksCatalogAndRestore()=runBlocking {
        Fixture().use { f->
            f.seed();val entered=CountDownLatch(1);val release=CountDownLatch(1)
            val c=f.coordinator(f.remote(entered=entered,release=release))
            val job=launch(Dispatchers.Default){c.refresh(MetadataLanguage.EN_US)}
            assertTrue(withContext(Dispatchers.IO){entered.await(10,TimeUnit.SECONDS)})
            try {
                f.repo.setPersonalRating(f.ids[0],9)
                f.repo.saveRecord(RecordDraft(movieId=f.ids[0],kind=RecordKind.NOTE,date="2020-01-01",body="Written during preparation"))
                assertTrue(runCatching{f.repo.addMovie(ViewingDraft(title="Blocked"))}.isFailure)
                assertTrue(runCatching{f.repo.deleteMovie(f.ids[0])}.isFailure)
                assertTrue(runCatching{c.refresh(MetadataLanguage.ZH_CN)}.isFailure)
                assertTrue(runCatching{f.access.reserve{}}.isFailure)
                val backups=BackupRepository(f.context,f.db,f.posters,f.drafts,f.access)
                val export=backups.prepareExport(); val data=BackupCodec().read(export.file,f.root)
                assertTrue(data.movies.all{it.title.startsWith("Old")});backups.cancelExport(export)
                f.assertOld()
            } finally {release.countDown()}
            job.join()
            assertTrue(c.state.value is MetadataRefreshState.Completed)
            assertEquals("en-US",f.db.dao().metadataState()!!.activeLanguage)
            assertEquals(6L,f.db.dao().metadataState()!!.generation)
            assertTrue(f.db.dao().allMovies().all{it.title.startsWith("English")})
            assertEquals(9,f.repo.movie(f.ids[0])!!.personalRatingHalfStars)
            assertEquals("Written during preparation",f.db.dao().allNotes().single().body)
            assertEquals(2,f.db.dao().allGenres().size)
            val snapshot=f.repo.snapshots.first()
            assertEquals("en-US",snapshot.metadata.activeLanguage)
            assertTrue(snapshot.movies.all{it.movie.metadataLanguage=="en-US" && it.movie.title.startsWith("English")})
            assertNotNull(f.posters.file(f.oldPoster))
        }
    }
    @Test fun cancellationAndLegitimatelyMissingPosterAreDifferent()=runBlocking {
        Fixture().use { f->
            f.seed();val entered=CountDownLatch(1);val release=CountDownLatch(1)
            val c=f.coordinator(f.remote(entered=entered,release=release))
            val job=launch(Dispatchers.Default){c.refresh(MetadataLanguage.EN_US)}
            assertTrue(withContext(Dispatchers.IO){entered.await(10,TimeUnit.SECONDS)})
            val cancelling=launch(Dispatchers.Default){c.cancel()};delay(50);release.countDown();cancelling.join();job.join()
            f.assertOld()
            f.coordinator(f.remote(noPoster=true)).refresh(MetadataLanguage.EN_US)
            assertEquals("en-US",f.db.dao().metadataState()!!.activeLanguage)
            assertTrue(f.db.dao().allMovies().all{it.posterKey==null})
        }
    }
}
