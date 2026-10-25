package com.seen.film.data

import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TmdbPosterTest {
    @Test fun transientFailureRetriesSameCdnWithoutCredential() = runBlocking {
        var calls = 0
        val repository = TmdbRepository({ error("Posters must not read credentials") }) { url, token, limit ->
            assertEquals("https://image.tmdb.org/t/p/w500/fixture.jpg", url)
            assertNull(token)
            assertEquals(20 * 1024 * 1024, limit)
            if (++calls == 1) throw SocketTimeoutException()
            byteArrayOf(42)
        }
        assertArrayEquals(byteArrayOf(42), repository.poster("/fixture.jpg"))
        assertEquals(2, calls)
    }

    @Test fun persistentFailureStopsAfterTwoAttempts() = runBlocking {
        var calls = 0
        val failure = SocketTimeoutException()
        val repository = TmdbRepository({ "" }) { _, _, _ -> calls++; throw failure }
        assertTrue(runCatching { repository.poster("/fixture.jpg") }.exceptionOrNull() is SocketTimeoutException)
        assertEquals(2, calls)
    }

    @Test fun tlsHttpAndValidationErrorsNeverRetry() = runBlocking {
        listOf(SSLHandshakeException("certificate failure"), IllegalStateException("HTTP failure"), IllegalArgumentException("oversized")).forEach { failure ->
            var calls = 0
            val repository = TmdbRepository({ "" }) { _, _, _ -> calls++; throw failure }
            val actual = runCatching { repository.poster("/fixture.jpg") }.exceptionOrNull()!!
            assertEquals(failure.javaClass, actual.javaClass)
            assertEquals(failure.message, actual.message)
            assertEquals(1, calls)
        }
    }

    @Test fun cancellationPreventsRetryAndInvalidPathNeverDownloads() = runBlocking {
        val job = Job()
        var calls = 0
        val repository = TmdbRepository({ "" }) { _, _, _ -> calls++; job.cancel(); throw SocketTimeoutException() }
        val request = async(job) { repository.poster("/fixture.jpg") }
        assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
        assertTrue(runCatching { repository.poster("/../secret") }.isFailure)
        assertEquals(1, calls)
    }
}
