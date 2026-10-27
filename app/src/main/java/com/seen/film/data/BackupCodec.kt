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

data class BackupData(val appVersion: String, val exportedAt: String, val movies: List<Movie>, val viewings: List<Viewing>, val assets: Map<String, File>)

/** A streaming document reader: retains entity metadata, but only one encoded poster at a time. */
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
        when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val names = hashSetOf<String>(); r.beginObject()
                while (r.hasNext()) { val key = r.nextName(); BackupRules.text(key, 1000000); require(names.add(key)) { "JSON 含重复字段" }; skip(r, depth + 1) }
                r.endObject()
            }
            JsonToken.BEGIN_ARRAY -> { r.beginArray(); while (r.hasNext()) skip(r, depth + 1); r.endArray() }
            else -> scalar(r)
        }
    }
    private fun record(r: JsonReader, fields: Set<String>, optional: Set<String> = emptySet()): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>(); val names = hashSetOf<String>()
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName(); BackupRules.text(name, 1000000)
            require(names.add(name)) { "JSON 含重复字段" }
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
    fun read(file: File, staging: File): BackupData {
        require(file.length() <= BackupRules.MAX_BYTES) { "备份超过 100 MiB" }
        val movies = mutableListOf<Movie>(); val viewings = mutableListOf<Viewing>(); val assets = linkedMapOf<String, File>()
        var version = ""; var exported = ""; var imageBytes = 0L
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        JsonReader(StrictJsonText(InputStreamReader(file.inputStream(), decoder))).use { r ->
            r.isLenient = false
            val names = hashSetOf<String>(); r.beginObject()
            while (r.hasNext()) {
                val name = r.nextName(); BackupRules.text(name, 1000000); require(names.add(name)) { "JSON 含重复字段" }
                when (name) {
                    "format" -> require(scalar(r) == "seen-backup") { "不是 Seen 备份" }
                    "schemaVersion" -> require(mapOf("v" to scalar(r)).long("v") == 1L) { "不支持此备份版本，请更新应用" }
                    "appVersion" -> { version = mapOf("v" to scalar(r)).string("v"); BackupRules.text(version, 64); require(version.isNotBlank()) }
                    "exportedAt" -> { exported = mapOf("v" to scalar(r)).string("v"); BackupRules.instant(exported) }
                    "movies" -> {
                        r.beginArray(); while (r.hasNext()) {
                            require(movies.size < 10000) { "电影数量超限" }
                            val m = record(r, movieFields, setOf("wantToWatch"))
                            movies += Movie(m.string("id"), m.string("title"), m.nullableString("originalTitle"), m.int("releaseYear"), m.nullableString("releaseDate"), m.nullableLong("tmdbId"), m.nullableString("posterAssetId"), m.string("source"), m.nullableString("directorNames"), m.int("runtimeMinutes"), m.nullableString("productionCountries"), m.string("createdAt"), m.string("updatedAt"), if ("wantToWatch" in m) m["wantToWatch"] as? Boolean ?: error("wantToWatch 必须为布尔值") else false).also(BackupRules::movie)
                        }; r.endArray()
                    }
                    "viewings" -> {
                        r.beginArray(); while (r.hasNext()) {
                            require(viewings.size < 100000) { "观看数量超限" }
                            val v = record(r, viewingFields)
                            viewings += Viewing(v.string("id"), v.string("movieId"), v.string("viewedDate"), v.nullableString("viewedTime"), v.nullableString("location"), v.int("ratingHalfStars"), v.nullableString("notes"), v.string("createdAt"), v.string("updatedAt")).also(BackupRules::viewing)
                        }; r.endArray()
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
        }
        BackupRules.relationships(movies, viewings, assets.keys)
        return BackupData(version, exported, movies, viewings, assets)
    }
    fun validateImage(file: File, expected: String? = null) {
        require(file.length() in 1..BackupRules.MAX_IMAGE.toLong()) { "海报超过 2 MiB 或为空" }
        if (expected != null) require(expected.matches(Regex("[a-f0-9]{64}")) && file.sha256() == expected) { "海报 SHA-256 不匹配" }
        file.inputStream().use { require(it.read() == 0xff && it.read() == 0xd8 && it.read() == 0xff) { "海报不是 JPEG" } }
        RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 4) { "JPEG 文件截断" }; input.seek(input.length() - 2)
            require(input.read() == 0xff && input.read() == 0xd9) { "JPEG 文件截断" }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048) { "海报尺寸或格式无效" }
        val bitmap = BitmapFactory.decodeFile(file.path) ?: error("海报无法解码"); bitmap.recycle()
    }
    fun write(file: File, data: BackupData) {
        JsonWriter(OutputStreamWriter(LimitedOutput(file.outputStream()), Charsets.UTF_8)).use { w ->
            w.beginObject()
            w.name("format").value("seen-backup"); w.name("schemaVersion").value(1)
            w.name("appVersion").value(data.appVersion); w.name("exportedAt").value(data.exportedAt)
            w.name("movies").beginArray()
            for (m in data.movies) {
                w.beginObject(); w.field("id", m.id); w.field("title", m.title); w.field("originalTitle", m.originalTitle)
                w.field("releaseYear", m.releaseYear); w.field("releaseDate", m.releaseDate); w.field("tmdbId", m.tmdbId)
                w.field("posterAssetId", m.posterKey); w.field("source", m.source); w.field("directorNames", m.directorNames)
                w.field("runtimeMinutes", m.runtimeMinutes); w.field("productionCountries", m.productionCountries)
                w.name("wantToWatch").value(m.wantToWatch)
                w.field("createdAt", m.createdAt); w.field("updatedAt", m.updatedAt); w.endObject()
            }; w.endArray(); w.name("viewings").beginArray()
            for (v in data.viewings) {
                w.beginObject(); w.field("id", v.id); w.field("movieId", v.movieId); w.field("viewedDate", v.viewedDate)
                w.field("viewedTime", v.viewedTime); w.field("location", v.location); w.field("ratingHalfStars", v.ratingHalfStars)
                w.field("notes", v.notes); w.field("createdAt", v.createdAt); w.field("updatedAt", v.updatedAt); w.endObject()
            }; w.endArray(); w.name("assets").beginArray()
            var total = 0L
            for ((id, asset) in data.assets) {
                validateImage(asset); total += asset.length(); require(total <= BackupRules.MAX_IMAGES_BYTES) { "海报总量超过 60 MiB" }
                w.beginObject(); w.field("id", id); w.field("mimeType", "image/jpeg"); w.field("sha256", asset.sha256())
                w.field("base64", Base64.getEncoder().encodeToString(asset.readBytes())); w.endObject()
            }; w.endArray(); w.endObject()
        }
    }
    private fun JsonWriter.field(name: String, value: Any?) {
        name(name); when (value) { null -> nullValue(); is Number -> value(value); is String -> value(value); else -> error("Unsupported value") }
    }
    companion object {
        private val movieFields = setOf("id", "title", "originalTitle", "releaseYear", "releaseDate", "tmdbId", "posterAssetId", "source", "directorNames", "runtimeMinutes", "productionCountries", "createdAt", "updatedAt")
        private val viewingFields = setOf("id", "movieId", "viewedDate", "viewedTime", "location", "ratingHalfStars", "notes", "createdAt", "updatedAt")
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
