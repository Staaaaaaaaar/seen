package com.seen.film.data

import com.seen.film.domain.MetadataLanguage

data class TmdbPage(val movies: List<TmdbMovie>, val page: Int, val totalPages: Int)
data class TmdbGenre(val id: Long, val name: String)
data class TmdbCredit(
    val creditKey: String, val personId: Long, val name: String, val isCast: Boolean,
    val department: String? = null, val job: String? = null, val character: String? = null,
    val profilePath: String? = null, val position: Int,
)
data class TmdbMovie(
    val id: Long, val title: String, val originalTitle: String?, val releaseDate: String?,
    val posterPath: String?, val directors: String?, val runtime: Int?, val countries: String?,
    val overview: String? = null, val genres: List<TmdbGenre> = emptyList(),
    val credits: List<TmdbCredit> = emptyList(), val language: MetadataLanguage = MetadataLanguage.ZH_CN,
)
