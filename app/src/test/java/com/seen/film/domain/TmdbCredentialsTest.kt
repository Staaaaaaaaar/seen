package com.seen.film.domain

import org.junit.Assert.*
import org.junit.Test

class TmdbCredentialsTest {
    @Test fun acceptsBearerTokenCharactersButRejectsHeadersPrefixesAndOversizedInput() {
        assertTrue(TmdbCredentials.valid("fixture.payload.signature_-="))
        listOf("", "Bearer fixture", "fixture\r\nInjected: value", "令牌", "a".repeat(8193)).forEach {
            assertFalse(TmdbCredentials.valid(it))
        }
    }
}
