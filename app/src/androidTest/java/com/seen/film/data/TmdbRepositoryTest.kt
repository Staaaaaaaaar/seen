package com.seen.film.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TmdbRepositoryTest {
    @Test fun malformedCredentialNeverReachesTransportOrErrorText() = runBlocking {
        var called = false
        val repository = TmdbRepository(token = { "sensitive-value\r\nInjected: secret" }, fetch = { _, _, _ -> called = true; byteArrayOf() })
        val error = runCatching { repository.search("电影") }.exceptionOrNull()
        assertNotNull(error)
        assertFalse(called)
        assertFalse(error!!.message.orEmpty().contains("sensitive-value"))
    }

    @Test fun searchEncodesQueryAndUsesBearerWithoutPuttingTokenInUrl() = runBlocking {
        val repository = TmdbRepository(token = { "fixture-token" }, fetch = { url, token, limit ->
            assertTrue(url.startsWith("https://api.themoviedb.org/3/search/movie?"))
            assertTrue(url.contains("language=zh-CN"))
            assertTrue(url.contains("page=2"))
            assertTrue(url.contains("%E4%B8%AD%E6%96%87+%26+Original"))
            assertFalse(url.contains("fixture-token"))
            assertEquals("fixture-token", token)
            assertEquals(2 * 1024 * 1024, limit)
            """{"total_pages":3,"results":[{"id":1,"title":"同名","original_title":"Original","release_date":"2001-01-01"},{"id":2,"title":"同名","release_date":"2020-01-01"}]}""".toByteArray()
        })
        val page = repository.search("中文 & Original", 2)
        assertEquals(2, page.page)
        assertEquals(3, page.totalPages)
        assertEquals(listOf("2001-01-01", "2020-01-01"), page.movies.map { it.releaseDate })
    }

    @Test fun missingCredentialTimeoutAndMalformedResponseDoNotReturnPartialSuccess() = runBlocking {
        var called = false
        val missing = TmdbRepository(token = { "" }, fetch = { _, _, _ -> called = true; byteArrayOf() })
        assertTrue(runCatching { missing.search("电影") }.isFailure)
        assertFalse(called)
        val timeout = TmdbRepository(token = { "fixture" }, fetch = { _, _, _ -> throw java.net.SocketTimeoutException() })
        assertTrue(runCatching { timeout.search("电影") }.exceptionOrNull() is java.net.SocketTimeoutException)
        val malformed = TmdbRepository(token = { "fixture" }, fetch = { _, _, _ -> "not json".toByteArray() })
        assertTrue(runCatching { malformed.search("电影") }.isFailure)
        assertTrue(runCatching { malformed.poster("/../bad") }.isFailure)
    }
}
