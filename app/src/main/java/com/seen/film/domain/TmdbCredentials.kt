package com.seen.film.domain

object TmdbCredentials {
    // Validate before using a value in an HTTP header; never echo it in errors.
    fun valid(value: String): Boolean = value.isNotEmpty() && value.length <= 8192 &&
        value.matches(Regex("[A-Za-z0-9._~+/-]+=*"))
}
