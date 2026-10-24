package com.seen.film.data

import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only these two TMDB hosts may receive the credential. No redirect or IP override. */
internal class TmdbApiTransport(private val fetch: (String, String?, Int) -> ByteArray) {
    @Volatile private var preferred = PRIMARY

    suspend fun request(path: String, credential: String, limit: Int): ByteArray {
        val first = preferred
        currentCoroutineContext().ensureActive()
        val bytes = try {
            fetch("$first/3/$path", credential, limit)
        } catch (error: IOException) {
            // Certificate/TLS errors must remain visible. HTTP/authentication and
            // response validation errors are not IOExceptions and never retry.
            if (error is SSLException) throw error
            currentCoroutineContext().ensureActive()
            val other = if (first == PRIMARY) ALTERNATE else PRIMARY
            val result = fetch("$other/3/$path", credential, limit)
            currentCoroutineContext().ensureActive()
            preferred = other
            return result
        }
        currentCoroutineContext().ensureActive()
        return bytes
    }

    companion object {
        const val PRIMARY = "https://api.tmdb.org"
        const val ALTERNATE = "https://api.themoviedb.org"
    }
}
