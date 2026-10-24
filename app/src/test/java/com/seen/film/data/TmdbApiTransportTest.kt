package com.seen.film.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TmdbApiTransportTest {
    @Test fun reachableTmdbHostIsUsedImmediatelyWithoutTryingOldHost() = runBlocking {
        val urls = mutableListOf<String>()
        val transport = TmdbApiTransport { url, _, _ -> urls += url; byteArrayOf(42) }
        transport.request("search/movie?query=test", "fixture", 1024)
        transport.request("movie/42", "fixture", 1024)
        assertEquals(listOf("https://api.tmdb.org/3/search/movie?query=test",
            "https://api.tmdb.org/3/movie/42"), urls)
    }

    @Test fun retriesNetworkFailureAndReusesReachableHostForLaterRequests() = runBlocking {
        val urls = mutableListOf<String>()
        val transport = TmdbApiTransport { url, credential, limit ->
            urls += url
            assertEquals("fixture-token", credential)
            assertEquals(1024, limit)
            assertFalse(url.contains("fixture-token"))
            if (url.startsWith(TmdbApiTransport.PRIMARY)) throw UnknownHostException()
            byteArrayOf(42)
        }
        assertArrayEquals(byteArrayOf(42), transport.request("search/movie?query=test", "fixture-token", 1024))
        transport.request("movie/42", "fixture-token", 1024)
        assertEquals(listOf("${TmdbApiTransport.PRIMARY}/3/search/movie?query=test",
            "${TmdbApiTransport.ALTERNATE}/3/search/movie?query=test", "${TmdbApiTransport.ALTERNATE}/3/movie/42"), urls)
    }

    @Test fun bothHostsFailWithOnlyTwoAttempts() = runBlocking {
        var calls = 0
        val transport = TmdbApiTransport { _, _, _ -> calls++; throw SocketTimeoutException() }
        assertTrue(runCatching { transport.request("movie/42", "fixture", 1024) }.exceptionOrNull() is SocketTimeoutException)
        assertEquals(2, calls)
    }

    @Test fun httpErrorsInvalidResponseAndTlsFailureNeverSwitchHosts() = runBlocking {
        listOf(IllegalStateException("401"), IllegalStateException("403"), IllegalStateException("429"),
            IllegalArgumentException("response too large"), SSLHandshakeException("certificate failure")).forEach { failure ->
            var calls = 0
            val transport = TmdbApiTransport { _, _, _ -> calls++; throw failure }
            assertSame(failure, runCatching { transport.request("movie/42", "fixture", 1024) }.exceptionOrNull())
            assertEquals(1, calls)
        }
    }

    @Test fun cancellationDuringFirstAttemptDoesNotStartAnotherRequest() = runBlocking {
        val requestJob = Job()
        var calls = 0
        val transport = TmdbApiTransport { _, _, _ -> calls++; requestJob.cancel(); throw ConnectException() }
        val result = async(requestJob) { transport.request("movie/42", "fixture", 1024) }
        assertTrue(runCatching { result.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
    }

    @Test fun alternateFailureCanRecoverToPrimaryWithoutLooping() = runBlocking {
        var primaryAvailable = false
        val urls = mutableListOf<String>()
        val transport = TmdbApiTransport { url, _, _ ->
            urls += url
            if (url.startsWith(TmdbApiTransport.PRIMARY) != primaryAvailable) throw ConnectException()
            byteArrayOf(1)
        }
        transport.request("movie/1", "fixture", 1024)
        primaryAvailable = true
        transport.request("movie/2", "fixture", 1024)
        transport.request("movie/3", "fixture", 1024)
        assertEquals(5, urls.size)
        assertTrue(urls.last().startsWith(TmdbApiTransport.PRIMARY))
    }
}
