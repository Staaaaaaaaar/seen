package com.seen.film.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.seen.film.domain.MetadataLanguage
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** HTTPS only, bounded responses and deadlines; never include tokens in URLs or errors. */
class TmdbRepository(
    private val token: () -> String,
    private val fetch: (String, String?, Int) -> ByteArray = ::download,
) {
    private val api = TmdbApiTransport(fetch)

    private suspend fun request(path: String): JSONObject {
        val credential = token().trim()
        check(credential.isNotEmpty()) { "请先在设置中配置电影资料访问凭据" }
        require(com.seen.film.domain.TmdbCredentials.valid(credential)) {
            "令牌格式无效，请在设置中检查 API Read Access Token"
        }
        return JSONObject(String(api.request(path, credential, 2 * 1024 * 1024), Charsets.UTF_8))
    }
    suspend fun search(query: String, page: Int = 1, language: MetadataLanguage = MetadataLanguage.ZH_CN): TmdbPage = withContext(Dispatchers.IO) {
        require(query.isNotBlank() && query.codePointCount(0, query.length) <= 300)
        require(page in 1..500)
        val json = request("search/movie?language=${language.apiCode}&include_adult=false&page=$page&query=${URLEncoder.encode(query.trim(), "UTF-8")}")
        val results = json.getJSONArray("results")
        TmdbPage((0 until results.length()).map { parse(results.getJSONObject(it), language) }, page, json.optInt("total_pages", 1).coerceAtMost(500))
    }
    suspend fun details(id: Long, language: MetadataLanguage = MetadataLanguage.ZH_CN): TmdbMovie = withContext(Dispatchers.IO) {
        require(id > 0)
        parse(request("movie/$id?language=${language.apiCode}&append_to_response=credits"), language)
    }
    suspend fun poster(path: String): ByteArray = withContext(Dispatchers.IO) {
        require(path.matches(Regex("/[A-Za-z0-9._-]+")))
        val url = "https://image.tmdb.org/t/p/w500$path"
        currentCoroutineContext().ensureActive()
        try {
            fetch(url, null, 20 * 1024 * 1024)
        } catch (error: java.io.IOException) {
            // Retry a transient connection failure once, retaining normal TLS checks.
            if (error is javax.net.ssl.SSLException) throw error
            delay(300)
            currentCoroutineContext().ensureActive()
            fetch(url, null, 20 * 1024 * 1024)
        }.also { currentCoroutineContext().ensureActive() }
    }
    suspend fun portrait(path: String): ByteArray = withContext(Dispatchers.IO) {
        require(path.matches(Regex("/[A-Za-z0-9._-]+")))
        currentCoroutineContext().ensureActive()
        fetch("https://image.tmdb.org/t/p/w185$path", null, 20 * 1024 * 1024).also { currentCoroutineContext().ensureActive() }
    }
    companion object {
        internal fun parse(j: JSONObject, language: MetadataLanguage = MetadataLanguage.ZH_CN): TmdbMovie {
            fun text(value: JSONObject, key: String, limit: Int = 300): String? {
                if (value.isNull(key)) return null
                val result = value.getString(key).takeIf { it.isNotBlank() } ?: return null
                require(result.codePointCount(0, result.length) <= limit) { "TMDB 资料字段过长" }
                return result
            }
            val creditObject = j.optJSONObject("credits")
            val cast = creditObject?.optJSONArray("cast")
            val crew = creditObject?.optJSONArray("crew")
            require((cast?.length() ?: 0) + (crew?.length() ?: 0) <= 1000) { "TMDB 演职人员过多" }
            val credits = mutableListOf<TmdbCredit>()
            for ((isCast, array) in listOf(true to cast, false to crew)) {
                if (array == null) continue
                for (index in 0 until array.length()) {
                    val c = array.getJSONObject(index)
                    val personId = if (c.has("id") && !c.isNull("id")) c.getLong("id") else null
                    // Historical/fixed director-only responses may lack a person ID.
                    if (personId == null) continue
                    require(personId > 0) { "TMDB 人员编号无效" }
                    val name = text(c, "name") ?: error("TMDB 人员缺少姓名")
                    val character = text(c, "character", 3000)
                    val job = text(c, "job", 1000)
                    val department = text(c, "department", 1000)
                    val profile = text(c, "profile_path")
                    require(profile == null || profile.matches(Regex("/[A-Za-z0-9._-]+"))) { "TMDB 人员头像路径无效" }
                    val key = text(c, "credit_id") ?: MessageDigest.getInstance("SHA-256")
                        .digest("$isCast:$personId:$character:$job:$department:$index".toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    credits += TmdbCredit(key, personId, name, isCast, department, job, character, profile, index)
                }
            }
            require(credits.map { it.creditKey }.toSet().size == credits.size) { "TMDB 演职人员编号重复" }
            val directors = crew?.let { (0 until it.length()).map { n -> it.getJSONObject(n) }
                .filter { it.optString("job") == "Director" }.mapNotNull { text(it, "name") }.distinct().joinToString(" / ") }?.ifBlank { null }
            val countryArray = j.optJSONArray("production_countries")
            require((countryArray?.length() ?: 0) <= 100) { "TMDB 国家资料过多" }
            val countries = countryArray?.let { (0 until it.length()).mapNotNull { n -> text(it.getJSONObject(n), "name") }.joinToString(" / ") }?.ifBlank { null }
            val genreArray = j.optJSONArray("genres")
            require((genreArray?.length() ?: 0) <= 100) { "TMDB 类型过多" }
            val genres = genreArray?.let { (0 until it.length()).map { n ->
                val g = it.getJSONObject(n); val id = g.getLong("id"); require(id > 0) { "TMDB 类型编号无效" }
                TmdbGenre(id, text(g, "name") ?: error("TMDB 类型缺少名称"))
            } }.orEmpty()
            require(genres.map { it.id }.toSet().size == genres.size) { "TMDB 类型编号重复" }
            val id = j.getLong("id"); require(id > 0)
            val title = text(j, "title") ?: text(j, "original_title") ?: error("电影缺少片名")
            return TmdbMovie(id, title, text(j,"original_title"), text(j,"release_date"), text(j,"poster_path"), directors,
                j.optInt("runtime").takeIf { it > 0 }, countries, text(j,"overview",50000), genres, credits, language)
        }
        private fun download(url: String, credential: String?, limit: Int): ByteArray {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                credential?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                connection.setRequestProperty("Accept", if (credential == null) "image/*" else "application/json")
                when (connection.responseCode) {
                    200 -> Unit
                    401, 403 -> error("访问凭据无效或无权限，请在设置中检查")
                    429 -> error("请求过于频繁，请稍后重试")
                    else -> error("电影资料暂不可用，请稍后重试")
                }
                return connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        require(output.size() + n <= limit) { "TMDB 响应过大" }
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                }
            } finally { connection.disconnect() }
        }
    }
}
