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
        require(m.personalRatingHalfStars == null || m.personalRatingHalfStars in 1..10) { "个人评分无效" }
        text(m.overview, 50000)
        require(m.metadataLanguage == null || m.metadataLanguage in setOf("zh-CN", "en-US")) { "资料语言无效" }
        m.posterKey?.let(::uuid); instant(m.createdAt); instant(m.updatedAt)
    }
    fun viewing(v: Viewing) {
        uuid(v.id); uuid(v.movieId); LocalDate.parse(v.viewedDate); v.viewedTime?.let { LocalTime.parse(it) }
        text(v.location, 200); text(v.notes, 50000)
        require(v.ratingHalfStars == null || v.ratingHalfStars in 1..10) { "评分无效" }
        instant(v.createdAt); instant(v.updatedAt)
    }
    fun relationships(movies: List<Movie>, viewings: List<Viewing>, assets: Set<String>, archives: List<ArchivedPoster> = emptyList()) {
        require(movies.size <= 10000 && viewings.size <= 100000 && assets.size <= 10000) { "备份记录数超限" }
        require(movies.map { it.id }.toSet().size == movies.size && viewings.map { it.id }.toSet().size == viewings.size) { "备份含重复 ID" }
        val tmdb = movies.mapNotNull { it.tmdbId }
        require(tmdb.toSet().size == tmdb.size) { "备份含重复 TMDB ID" }
        val ids = movies.map { it.id }.toSet()
        require(viewings.all { it.movieId in ids }) { "观看引用了不存在的电影" }
        require((movies.mapNotNull { it.posterKey } + archives.map { it.posterKey }).toSet() == assets) { "海报引用缺失或存在未引用海报" }
    }
    fun note(n: MovieNote) {
        uuid(n.id); uuid(n.movieId); LocalDate.parse(n.recordedDate); n.recordedTime?.let { LocalTime.parse(it) }
        text(n.location, 200); text(n.body, 50000); require(n.body.isNotBlank()) { "留言不能为空" }
        instant(n.createdAt); instant(n.updatedAt)
    }
    fun genre(g: MovieGenre) {
        uuid(g.movieId); require(g.genreId > 0 && g.position >= 0) { "类型编号或顺序无效" }
        text(g.name, 300); require(g.name.isNotBlank()) { "类型名称不能为空" }
    }
    fun credit(c: MovieCredit) {
        uuid(c.movieId); require(c.personId > 0 && c.position >= 0) { "演职人员编号或顺序无效" }
        text(c.creditKey, 300); require(c.creditKey.isNotBlank())
        text(c.name, 300); require(c.name.isNotBlank())
        text(c.department, 1000); text(c.job, 1000); text(c.character, 3000); text(c.profilePath, 300)
        require(c.profilePath == null || c.profilePath.matches(Regex("/[A-Za-z0-9._-]+"))) { "人员头像路径无效" }
    }
    fun validate(data: BackupData) {
        text(data.appVersion, 64); require(data.appVersion.isNotBlank()); instant(data.exportedAt)
        data.movies.forEach(::movie); data.viewings.forEach(::viewing); data.notes.forEach(::note)
        data.genres.forEach(::genre); data.credits.forEach(::credit)
        relationships(data.movies, data.viewings, data.assets.keys, data.archivedPosters)
        require(data.notes.size <= 100000 && data.genres.size <= 100000 && data.credits.size <= 100000 && data.archivedPosters.size <= 10000) { "扩展记录数超限" }
        val ids = data.movies.map { it.id }.toSet()
        require(data.notes.all { it.movieId in ids } && data.genres.all { it.movieId in ids } && data.credits.all { it.movieId in ids } && data.archivedPosters.all { it.movieId in ids }) { "记录引用了不存在的电影" }
        require(data.notes.map { it.id }.toSet().size == data.notes.size) { "留言 ID 重复" }
        require(data.genres.map { it.movieId to it.genreId }.toSet().size == data.genres.size) { "电影类型重复" }
        require(data.credits.map { it.movieId to it.creditKey }.toSet().size == data.credits.size) { "演职人员记录重复" }
        require(data.archivedPosters.map { it.movieId to it.posterKey }.toSet().size == data.archivedPosters.size) { "归档海报重复" }
        data.archivedPosters.forEach { uuid(it.movieId); uuid(it.posterKey) }
    }

}
