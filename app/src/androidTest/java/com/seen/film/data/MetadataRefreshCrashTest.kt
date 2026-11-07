package com.seen.film.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.seen.film.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Controlled exclusively by the external process-kill verification script. */
class MetadataRefreshCrashTest {
    private val base get()=ApplicationProvider.getApplicationContext<Context>()
    private val root get()=base.getDir("metadata-crash-fixture",Context.MODE_PRIVATE)
    private fun phase()=InstrumentationRegistry.getArguments().getString("metadataCrashPhase")
    private fun context()=object:ContextWrapper(base){
        override fun getFilesDir()=File(root,"files").apply{mkdirs()}
        override fun getCacheDir()=File(root,"cache").apply{mkdirs()}
        override fun getDatabasePath(name:String)=File(root,name)
    }
    private fun jpeg(color:Int):ByteArray {
        val bitmap=Bitmap.createBitmap(20,30,Bitmap.Config.ARGB_8888).apply{eraseColor(color)}
        return java.io.ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,88,it);bitmap.recycle();it.toByteArray()}
    }
    @Test fun prepareAndWaitForExternalKill()=runBlocking {
        val phase=phase();assumeTrue("External controller required",phase!=null)
        root.deleteRecursively();root.mkdirs();val context=context()
        val db=Room.databaseBuilder(context,SeenDatabase::class.java,"fixture.db").build()
        try{
            val access=LibraryAccess();val posters=PosterStore(context);val drafts=DraftStore(context,access)
            val repo=SeenRepository(db,access);val key=posters.importDownloaded(jpeg(Color.RED))
            val id=repo.addMovie(ViewingDraft(title="Old",tmdbId=42,posterKey=key))
            repo.setPersonalRating(id,9)
            repo.saveRecord(RecordDraft(movieId=id,kind=RecordKind.NOTE,date="2000-01-01",body="Survives a crash"))
            db.dao().insertArchivedPosters(listOf(ArchivedPoster(id,key)))
            db.dao().putMetadataState(LibraryMetadataState(activeLanguage="zh-CN",generation=3))
            val remote=TmdbRepository(token={"fixture"},fetch={url,_,_->
                if(url.contains("image.tmdb.org"))jpeg(Color.GREEN)
                else """{"id":42,"title":"New","poster_path":"/new.jpg","genres":[{"id":18,"name":"Drama"}]}""".toByteArray()
            })
            val coordinator=MetadataRefreshCoordinator(context,db,posters,drafts,access,remote,checkpoint={
                if(it==phase){File(root,"ready").writeText(it);while(true)delay(1000)}
            })
            coordinator.refresh(MetadataLanguage.EN_US)
            fail("External controller did not terminate the process")
        }finally{db.close()}
    }
    @Test fun verifyAfterExternalKill()=runBlocking {
        val phase=phase();assumeTrue("External controller required",phase!=null)
        val context=context();val db=Room.databaseBuilder(context,SeenDatabase::class.java,"fixture.db").build()
        try{
            val access=LibraryAccess();val posters=PosterStore(context);val drafts=DraftStore(context,access)
            val remote=TmdbRepository(token={"fixture"},fetch={_,_,_->error("Recovery must not use network")})
            MetadataRefreshCoordinator(context,db,posters,drafts,access,remote).recoverInterrupted()
            val movie=db.dao().allMovies().single();val state=db.dao().metadataState()!!
            val committed=phase=="committed"
            assertEquals(if(committed)"New" else "Old",movie.title)
            assertEquals(if(committed)"en-US" else "zh-CN",state.activeLanguage)
            assertEquals(if(committed)4L else 3L,state.generation)
            assertEquals(9,movie.personalRatingHalfStars)
            assertEquals("Survives a crash",db.dao().allNotes().single().body)
            assertEquals(if(committed)1 else 0,db.dao().allGenres().size)
            val image=BitmapFactory.decodeFile(posters.file(movie.posterKey)!!.path)
            val pixel=image.getPixel(5,5);image.recycle()
            assertTrue(if(committed)Color.green(pixel)>Color.red(pixel) else Color.red(pixel)>Color.green(pixel))
            assertTrue(db.dao().allArchivedPosters().all{posters.file(it.posterKey)!=null})
        }finally{db.close();root.deleteRecursively()}
    }
}
