package com.seen.film.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TmdbCredentialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun isolated(preferences: SharedPreferences) = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int) = preferences
    }

    @Test fun persistsRemovesAndRejectsMalformedTokenWithoutEchoingIt() = runBlocking {
        val name = "credential-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, 0)
        try {
            val store = TmdbCredentialStore(isolated(preferences))
            store.save("  fixture.token  ")
            assertEquals("fixture.token", TmdbCredentialStore(isolated(preferences)).token())
            val error = runCatching { store.save("private-value\r\nInjected: secret") }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(error!!.message.orEmpty().contains("private-value"))
            assertEquals("fixture.token", store.token())
            store.save("")
            assertEquals("", store.token())
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun failedDiskCommitIsReported() = runBlocking {
        val name = "credential-failure-${UUID.randomUUID()}"
        val original = context.getSharedPreferences(name, 0)
        val failing = object : SharedPreferences by original {
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by original.edit() {
                override fun commit() = false
            }
        }
        try {
            assertTrue(runCatching { TmdbCredentialStore(isolated(failing)).save("fixture.token") }.isFailure)
            assertFalse(original.contains("token"))
        } finally { context.deleteSharedPreferences(name) }
    }
}
