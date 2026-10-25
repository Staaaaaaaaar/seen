package com.seen.film.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Protocol rules are independent of Room schema and device clock. Never normalize imported text. */
object BackupRules {
    const val MAX_BYTES = 100L * 1024 * 1024
    const val MAX_IMAGE = 2 * 1024 * 1024
    const val MAX_IMAGES_BYTES = 60L * 1024 * 1024
    fun uuid(value: String) { require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) { "备份 ID 必须为规范小写 UUID" } }
    fun text(value: String?, limit: Int) {
        if (value == null) return
        require(value.codePointCount(0, value.length) <= limit) { "备份文本超过 $limit 个字符" }
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c.isHighSurrogate()) require(i < value.length && value[i++].isLowSurrogate()) { "备份包含非法 Unicode" }
            else require(!c.isLowSurrogate()) { "备份包含非法 Unicode" }
        }
    }
    fun instant(value: String) { Instant.parse(value) }
    fun movie(m: Movie) {
        uuid(m.id); require(m.title.isNotBlank()) { "电影名称不能为空" }
        text(m.title, 300); text(m.originalTitle, 300); text(m.directorNames, 10000); text(m.productionCountries, 10000)
        require(m.releaseYear == null || m.releaseYear in 1800..9999) { "上映年份无效" }
        m.releaseDate?.let { LocalDate.parse(it) }
        require(m.tmdbId == null || m.tmdbId > 0) { "TMDB ID 无效" }
        require(m.runtimeMinutes == null || m.runtimeMinutes >= 0) { "片长无效" }
        require(m.source in setOf("MANUAL", "TMDB")) { "电影来源无效" }
        m.posterKey?.let(::uuid); instant(m.createdAt); instant(m.updatedAt)
    }
    fun viewing(v: Viewing) {
        uuid(v.id); uuid(v.movieId); LocalDate.parse(v.viewedDate); v.viewedTime?.let { LocalTime.parse(it) }
        text(v.location, 200); text(v.notes, 50000)
        require(v.ratingHalfStars == null || v.ratingHalfStars in 1..10) { "评分无效" }
        instant(v.createdAt); instant(v.updatedAt)
    }
    fun relationships(movies: List<Movie>, viewings: List<Viewing>, assets: Set<String>) {
        require(movies.size <= 10000 && viewings.size <= 100000 && assets.size <= 10000) { "备份记录数超限" }
        require(movies.map { it.id }.toSet().size == movies.size && viewings.map { it.id }.toSet().size == viewings.size) { "备份含重复 ID" }
        val tmdb = movies.mapNotNull { it.tmdbId }
        require(tmdb.toSet().size == tmdb.size) { "备份含重复 TMDB ID" }
        val ids = movies.map { it.id }.toSet()
        require(viewings.all { it.movieId in ids }) { "观看引用了不存在的电影" }
        require(movies.mapNotNull { it.posterKey }.toSet() == assets) { "海报引用缺失或存在未引用海报" }
    }
}
