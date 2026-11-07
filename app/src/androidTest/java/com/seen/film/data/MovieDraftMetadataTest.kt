package com.seen.film.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.seen.film.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class MovieDraftMetadataTest {
    @Test fun extendedTmdbDetailsSurviveDraftRestartAndMovieOnlySave()=runBlocking {
        val parent=ApplicationProvider.getApplicationContext<Context>()
        val folder=File(parent.cacheDir,"movie-metadata-draft-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(parent){override fun getFilesDir()=folder}
        val db=Room.inMemoryDatabaseBuilder(context,SeenDatabase::class.java).build()
        try{
            val metadata=TmdbRepository.parse(JSONObject("""{"id":42,"title":"Film","overview":"A story","genres":[{"id":878,"name":"Science Fiction"}],"credits":{"cast":[{"id":2,"credit_id":"a","name":"Actor","character":"Role"}],"crew":[{"id":1,"credit_id":"d","name":"Director","job":"Director"}]}}"""),MetadataLanguage.EN_US)
            val draft=ViewingDraft(title="Film",tmdbId=42,movieOnly=true,metadata=metadata,directorNames=metadata.directors)
            DraftStore(context).write(draft)
            val recovered=DraftStore(context).read()!!
            assertEquals(draft,recovered)
            val id=SeenRepository(db).addMovie(recovered)
            assertEquals("A story",db.dao().movie(id)!!.overview)
            assertEquals("en-US",db.dao().movie(id)!!.metadataLanguage)
            assertEquals(878L,db.dao().allGenres().single().genreId)
            assertEquals(2,db.dao().allCredits().size)
            assertTrue(db.dao().allViewings().isEmpty())
        }finally{db.close();folder.deleteRecursively()}
    }
}
