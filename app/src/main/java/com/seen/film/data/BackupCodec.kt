package com.seen.film.data

import android.graphics.BitmapFactory
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.*
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

internal fun File.sha256(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(16384)
    while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    digest.digest().joinToString("") { "%02x".format(it) }
}

data class BackupData(
    val appVersion: String, val exportedAt: String, val movies: List<Movie>,
    val viewings: List<Viewing>, val assets: Map<String, File>,
    val notes: List<MovieNote> = emptyList(), val genres: List<MovieGenre> = emptyList(),
    val credits: List<MovieCredit> = emptyList(), val archivedPosters: List<ArchivedPoster> = emptyList(),
)

/** Strict streaming protocol; media is decoded one image at a time. */
class BackupCodec {
    private data class NumberValue(val raw: String)
    private fun scalar(r: JsonReader): Any? = when (r.peek()) {
        JsonToken.NULL -> { r.nextNull(); null }
        JsonToken.STRING -> r.nextString().also { BackupRules.text(it, (BackupRules.MAX_BYTES / 2).toInt()) }
        JsonToken.NUMBER -> NumberValue(r.nextString())
        JsonToken.BOOLEAN -> r.nextBoolean()
        else -> error("备份字段类型错误")
    }
    private fun skip(r: JsonReader, depth: Int) {
        require(depth <= 16) { "JSON 嵌套过深" }
        when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val names = hashSetOf<String>(); r.beginObject()
                while (r.hasNext()) { val name = r.nextName(); BackupRules.text(name, 1000000); require(names.add(name)) { "JSON 含重复字段" }; skip(r, depth + 1) }
                r.endObject()
            }
            JsonToken.BEGIN_ARRAY -> { r.beginArray(); while (r.hasNext()) skip(r, depth + 1); r.endArray() }
            else -> scalar(r)
        }
    }
    private fun record(r: JsonReader, fields: Set<String>, optional: Set<String> = emptySet()): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>(); val names = hashSetOf<String>(); r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName(); BackupRules.text(name, 1000000); require(names.add(name)) { "JSON 含重复字段" }
            if (name in fields || name in optional) result[name] = scalar(r) else skip(r, 4)
        }
        r.endObject(); require(result.keys.containsAll(fields)) { "备份缺少必需字段" }; return result
    }
    private fun Map<String, Any?>.string(key: String): String = get(key) as? String ?: error("$key 必须为字符串")
    private fun Map<String, Any?>.nullableString(key: String): String? = if (get(key) == null) null else string(key)
    private fun Map<String, Any?>.long(key: String): Long {
        val raw = (get(key) as? NumberValue)?.raw ?: error("$key 必须为整数")
        require(raw.matches(Regex("-?(0|[1-9][0-9]*)"))) { "$key 必须为整数" }
        return raw.toLongOrNull() ?: error("$key 整数溢出")
    }
    private fun Map<String, Any?>.nullableLong(key: String): Long? = if (get(key) == null) null else long(key)
    private fun Map<String, Any?>.int(key: String): Int? = nullableLong(key)?.also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "$key 整数溢出" } }?.toInt()
    private fun Map<String, Any?>.boolean(key: String): Boolean = get(key) as? Boolean ?: error("$key 必须为布尔值")
    private fun <T> readArray(r: JsonReader, target: MutableList<T>, limit: Int, read: () -> T) {
        r.beginArray(); while (r.hasNext()) { require(target.size < limit) { "备份记录数超限" }; target += read() }; r.endArray()
    }

    fun read(file: File, staging: File): BackupData {
        require(file.length() <= BackupRules.MAX_BYTES) { "备份超过 100 MiB" }
        val movies = mutableListOf<Movie>(); val viewings = mutableListOf<Viewing>()
        val notes = mutableListOf<MovieNote>(); val genres = mutableListOf<MovieGenre>()
        val credits = mutableListOf<MovieCredit>(); val archives = mutableListOf<ArchivedPoster>()
        val assets = linkedMapOf<String, File>(); var version = ""; var exported = ""
        var protocol = 0; var completeMovies = true; var imageBytes = 0L
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        JsonReader(StrictJsonText(InputStreamReader(file.inputStream(), decoder))).use { r ->
            r.isLenient = false; val names = hashSetOf<String>(); r.beginObject()
            while (r.hasNext()) {
                val name = r.nextName(); BackupRules.text(name, 1000000); require(names.add(name)) { "JSON 含重复字段" }
                when (name) {
                    "format" -> require(scalar(r) == "seen-backup") { "不是 Seen 备份" }
                    "schemaVersion" -> { val value = mapOf("v" to scalar(r)).long("v"); require(value in 1L..2L) { "不支持此备份版本，请更新应用" }; protocol = value.toInt() }
                    "appVersion" -> { version = mapOf("v" to scalar(r)).string("v"); BackupRules.text(version, 64); require(version.isNotBlank()) }
                    "exportedAt" -> { exported = mapOf("v" to scalar(r)).string("v"); BackupRules.instant(exported) }
                    "movies" -> readArray(r, movies, 10000) {
                        val m = record(r, movieFields, setOf("wantToWatch") + newMovieFields)
                        completeMovies = completeMovies && m.keys.containsAll(newMovieFields)
                        Movie(m.string("id"), m.string("title"), m.nullableString("originalTitle"), m.int("releaseYear"), m.nullableString("releaseDate"), m.nullableLong("tmdbId"), m.nullableString("posterAssetId"), m.string("source"), m.nullableString("directorNames"), m.int("runtimeMinutes"), m.nullableString("productionCountries"), m.string("createdAt"), m.string("updatedAt"), if ("wantToWatch" in m) m.boolean("wantToWatch") else false, m.int("personalRatingHalfStars"), m.nullableString("overview"), m.nullableString("metadataLanguage")).also(BackupRules::movie)
                    }
                    "viewings" -> readArray(r, viewings, 100000) {
                        val v = record(r, viewingFields)
                        Viewing(v.string("id"), v.string("movieId"), v.string("viewedDate"), v.nullableString("viewedTime"), v.nullableString("location"), v.int("ratingHalfStars"), v.nullableString("notes"), v.string("createdAt"), v.string("updatedAt")).also(BackupRules::viewing)
                    }
                    "notes" -> readArray(r, notes, 100000) {
                        val n = record(r, noteFields)
                        MovieNote(n.string("id"), n.string("movieId"), n.string("recordedDate"), n.nullableString("recordedTime"), n.nullableString("location"), n.string("body"), n.string("createdAt"), n.string("updatedAt")).also(BackupRules::note)
                    }
                    "genres" -> readArray(r, genres, 100000) {
                        val g = record(r, setOf("movieId", "genreId", "name", "position"))
                        MovieGenre(g.string("movieId"), g.long("genreId"), g.string("name"), g.int("position") ?: error("类型顺序不能为空")).also(BackupRules::genre)
                    }
                    "credits" -> readArray(r, credits, 100000) {
                        val c = record(r, creditFields)
                        MovieCredit(c.string("movieId"), c.string("creditKey"), c.long("personId"), c.string("name"), c.boolean("isCast"), c.nullableString("department"), c.nullableString("job"), c.nullableString("character"), c.nullableString("profilePath"), c.int("position") ?: error("人员顺序不能为空")).also(BackupRules::credit)
                    }
                    "archivedPosters" -> readArray(r, archives, 10000) {
                        val a = record(r, setOf("movieId", "posterAssetId"))
                        ArchivedPoster(a.string("movieId"), a.string("posterAssetId"))
                    }
                    "assets" -> {
                        r.beginArray(); while (r.hasNext()) {
                            require(assets.size < 10000) { "海报数量超限" }
                            val a = record(r, setOf("id", "mimeType", "sha256", "base64"))
                            val id = a.string("id"); BackupRules.uuid(id); require(id !in assets) { "海报 ID 重复" }
                            require(a.string("mimeType") == "image/jpeg") { "海报类型无效" }
                            val encoded = a.string("base64")
                            require(encoded.length <= ((BackupRules.MAX_IMAGE + 2) / 3) * 4) { "海报超过 2 MiB" }
                            val bytes = Base64.getDecoder().decode(encoded)
                            require(Base64.getEncoder().encodeToString(bytes) == encoded && bytes.size <= BackupRules.MAX_IMAGE) { "海报 Base64 无效或超限" }
                            imageBytes += bytes.size; require(imageBytes <= BackupRules.MAX_IMAGES_BYTES) { "海报总量超过 60 MiB" }
                            val target = File.createTempFile("asset-", ".jpg", staging); target.writeBytes(bytes)
                            validateImage(target, a.string("sha256")); assets[id] = target
                        }; r.endArray()
                    }
                    else -> skip(r, 2)
                }
            }
            r.endObject(); require(r.peek() == JsonToken.END_DOCUMENT) { "JSON 存在尾随内容" }
            require(names.containsAll(setOf("format", "schemaVersion", "appVersion", "exportedAt", "movies", "viewings", "assets"))) { "备份缺少必需字段" }
            if (protocol == 2) require(completeMovies && names.containsAll(setOf("notes", "genres", "credits", "archivedPosters"))) { "备份缺少新版数据字段" }
        }
        val converted = if (protocol == 1) {
            val grouped = viewings.groupBy { it.movieId }
            movies.map { movie -> movie.copy(personalRatingHalfStars = grouped[movie.id]?.sortedWith(com.seen.film.domain.viewingOrder)?.firstOrNull()?.ratingHalfStars,
                metadataLanguage = if (movie.tmdbId != null) "zh-CN" else null) }
        } else movies
        val archived = if (protocol == 1) movies.mapNotNull { m -> m.posterKey?.let { ArchivedPoster(m.id, it) } } else archives
        val data = BackupData(version, exported, converted, viewings, assets, notes, genres, credits, archived)
        BackupRules.validate(data)
        return data
    }

    fun validateImage(file: File, expected: String? = null) {
        require(file.length() in 1..BackupRules.MAX_IMAGE.toLong()) { "海报超过 2 MiB 或为空" }
        if (expected != null) require(expected.matches(Regex("[a-f0-9]{64}")) && file.sha256() == expected) { "海报 SHA-256 不匹配" }
        file.inputStream().use { require(it.read() == 0xff && it.read() == 0xd8 && it.read() == 0xff) { "海报不是 JPEG" } }
        RandomAccessFile(file, "r").use { input -> require(input.length() >= 4) { "JPEG 文件截断" }; input.seek(input.length()-2); require(input.read() == 0xff && input.read() == 0xd9) { "JPEG 文件截断" } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048) { "海报尺寸或格式无效" }
        val bitmap = BitmapFactory.decodeFile(file.path) ?: error("海报无法解码"); bitmap.recycle()
    }
    fun write(file: File, data: BackupData) {
        BackupRules.validate(data)
        JsonWriter(OutputStreamWriter(LimitedOutput(file.outputStream()), Charsets.UTF_8)).use { w ->
            w.beginObject(); w.name("format").value("seen-backup"); w.name("schemaVersion").value(2)
            w.name("appVersion").value(data.appVersion); w.name("exportedAt").value(data.exportedAt)
            w.records("movies", data.movies) { m -> record("id" to m.id, "title" to m.title, "originalTitle" to m.originalTitle, "releaseYear" to m.releaseYear, "releaseDate" to m.releaseDate, "tmdbId" to m.tmdbId, "posterAssetId" to m.posterKey, "source" to m.source, "directorNames" to m.directorNames, "runtimeMinutes" to m.runtimeMinutes, "productionCountries" to m.productionCountries, "wantToWatch" to m.wantToWatch, "createdAt" to m.createdAt, "updatedAt" to m.updatedAt, "personalRatingHalfStars" to m.personalRatingHalfStars, "overview" to m.overview, "metadataLanguage" to m.metadataLanguage) }
            w.records("viewings", data.viewings) { v -> record("id" to v.id, "movieId" to v.movieId, "viewedDate" to v.viewedDate, "viewedTime" to v.viewedTime, "location" to v.location, "ratingHalfStars" to v.ratingHalfStars, "notes" to v.notes, "createdAt" to v.createdAt, "updatedAt" to v.updatedAt) }
            w.records("notes", data.notes) { n -> record("id" to n.id, "movieId" to n.movieId, "recordedDate" to n.recordedDate, "recordedTime" to n.recordedTime, "location" to n.location, "body" to n.body, "createdAt" to n.createdAt, "updatedAt" to n.updatedAt) }
            w.records("genres", data.genres) { g -> record("movieId" to g.movieId, "genreId" to g.genreId, "name" to g.name, "position" to g.position) }
            w.records("credits", data.credits) { c -> record("movieId" to c.movieId, "creditKey" to c.creditKey, "personId" to c.personId, "name" to c.name, "isCast" to c.isCast, "department" to c.department, "job" to c.job, "character" to c.character, "profilePath" to c.profilePath, "position" to c.position) }
            w.records("archivedPosters", data.archivedPosters) { a -> record("movieId" to a.movieId, "posterAssetId" to a.posterKey) }
            w.name("assets").beginArray(); var total = 0L
            for ((id, asset) in data.assets) {
                validateImage(asset); total += asset.length(); require(total <= BackupRules.MAX_IMAGES_BYTES) { "海报总量超过 60 MiB" }
                w.record("id" to id, "mimeType" to "image/jpeg", "sha256" to asset.sha256(), "base64" to Base64.getEncoder().encodeToString(asset.readBytes()))
            }
            w.endArray(); w.endObject()
        }
    }
    private fun JsonWriter.record(vararg fields: Pair<String, Any?>) {
        beginObject(); for ((key, value) in fields) {
            name(key); when (value) { null -> nullValue(); is Number -> value(value); is String -> value(value); is Boolean -> value(value); else -> error("Unsupported value") }
        }; endObject()
    }
    private fun <T> JsonWriter.records(name: String, items: List<T>, block: JsonWriter.(T) -> Unit) {
        name(name).beginArray(); items.forEach { block(it) }; endArray()
    }
    companion object {
        private val movieFields = setOf("id", "title", "originalTitle", "releaseYear", "releaseDate", "tmdbId", "posterAssetId", "source", "directorNames", "runtimeMinutes", "productionCountries", "createdAt", "updatedAt")
        private val newMovieFields = setOf("personalRatingHalfStars", "overview", "metadataLanguage")
        private val viewingFields = setOf("id", "movieId", "viewedDate", "viewedTime", "location", "ratingHalfStars", "notes", "createdAt", "updatedAt")
        private val noteFields = setOf("id", "movieId", "recordedDate", "recordedTime", "location", "body", "createdAt", "updatedAt")
        private val creditFields = setOf("movieId", "creditKey", "personId", "name", "isCast", "department", "job", "character", "profilePath", "position")
    }
}

internal class LimitedOutput(private val output: OutputStream) : OutputStream() {
    private var count = 0L
    override fun write(b: Int) { require(++count <= BackupRules.MAX_BYTES) { "备份超过 100 MiB" }; output.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) { count += len; require(count <= BackupRules.MAX_BYTES) { "备份超过 100 MiB" }; output.write(b, off, len) }
    override fun flush() = output.flush()
    override fun close() = output.close()
}

/** Android's reader accepts raw control characters in strings; enforce lexical JSON here. */
internal class StrictJsonText(private val source: Reader) : Reader() {
    private var inString = false
    private var escape = false
    private var unicode = 0
    private var depth = 0
    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val count = source.read(buffer, offset, length)
        for (i in offset until offset + count.coerceAtLeast(0)) {
            val c = buffer[i]
            if (inString) {
                when {
                    unicode > 0 -> { require(c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F') { "非法 JSON 转义" }; unicode-- }
                    escape -> { require(c in "\"\\/bfnrtu") { "非法 JSON 转义" }; if (c == 'u') unicode = 4; escape = false }
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                    else -> require(c.code >= 32) { "JSON 字符串包含未转义控制字符" }
                }
            } else when (c) {
                '"' -> inString = true
                '{', '[' -> { depth++; require(depth <= 16) { "JSON 嵌套超过 16 层" } }
                '}', ']' -> depth--
            }
        }
        return count
    }
    override fun close() = source.close()
}
