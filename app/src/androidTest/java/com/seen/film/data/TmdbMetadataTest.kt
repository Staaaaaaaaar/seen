package com.seen.film.data

import com.seen.film.domain.MetadataLanguage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TmdbMetadataTest {
    private val fixture = """{"id":42,"title":"A Film","original_title":"Original","release_date":"2020-01-01","overview":"A story","poster_path":null,"genres":[{"id":878,"name":"Science Fiction"}],"credits":{"cast":[{"id":12,"credit_id":"cast-a","name":"Actor","character":"First","profile_path":null},{"id":12,"credit_id":"cast-b","name":"Actor","character":"Second"}],"crew":[{"id":13,"credit_id":"crew-a","name":"Director","job":"Director","department":"Directing"}]}}"""

    @Test fun localizedRequestsPreserveGenreIdsAndDistinctRoles() = runBlocking {
        val requests = mutableListOf<String>()
        val repo = TmdbRepository(token = { "fixture-token" }, fetch = { url, _, _ ->
            requests += url
            if (url.contains("search/movie")) """{"total_pages":1,"results":[{"id":42,"title":"A Film"}]}""".toByteArray() else fixture.toByteArray()
        })
        repo.search("电影", language = MetadataLanguage.EN_US)
        val movie = repo.details(42, MetadataLanguage.EN_US)
        assertTrue(requests.all { "language=en-US" in it })
        assertTrue(requests.last().contains("append_to_response=credits"))
        assertEquals("A story", movie.overview); assertNull(movie.posterPath)
        assertEquals(878L, movie.genres.single().id)
        assertEquals(listOf("First", "Second"), movie.credits.filter { it.isCast }.map { it.character })
        assertEquals(3, movie.credits.size); assertEquals("Director", movie.directors)
        assertEquals(MetadataLanguage.EN_US, movie.language)
        repo.details(42, MetadataLanguage.ZH_CN)
        assertTrue(requests.last().contains("language=zh-CN"))
    }

    @Test fun absentOptionalMetadataIsValidButExcessiveCreditsAreRejected() {
        val movie = TmdbRepository.parse(JSONObject("""{"id":42,"title":"Film","poster_path":null}"""))
        assertNull(movie.overview); assertTrue(movie.genres.isEmpty()); assertTrue(movie.credits.isEmpty())
        val json = JSONObject(fixture)
        val cast = JSONArray()
        repeat(1001) { cast.put(JSONObject().put("id", it+1).put("name", "Actor")) }
        json.getJSONObject("credits").put("cast",cast)
        assertTrue(runCatching { TmdbRepository.parse(json) }.isFailure)
    }

    @Test fun malformedGenresAndConflictingCreditIdsDoNotOverwriteSavedData() {
        val json = JSONObject(fixture)
        json.getJSONArray("genres").getJSONObject(0).put("id",-1)
        assertTrue(runCatching { TmdbRepository.parse(json) }.isFailure)
        val duplicate = JSONObject(fixture)
        duplicate.getJSONObject("credits").getJSONArray("cast").getJSONObject(1).put("credit_id","cast-a")
        assertTrue(runCatching { TmdbRepository.parse(duplicate) }.isFailure)
    }
}
