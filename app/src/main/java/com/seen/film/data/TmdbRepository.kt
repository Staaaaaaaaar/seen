package com.seen.film.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** HTTPS only, bounded responses and deadlines; never include tokens in URLs or errors. */
class TmdbRepository(
    private val token: () -> String,
    private val fetch: (String, String?, Int) -> ByteArray = ::download,
) {
    private fun request(path: String): JSONObject {
        val credential = token().trim()
        check(credential.isNotEmpty()) { "尚未配置 TMDB Read Access Token，仍可手动添加电影" }
        require(com.seen.film.domain.TmdbCredentials.valid(credential)) {
            "令牌格式无效，请在设置中检查 API Read Access Token"
        }
        return JSONObject(String(fetch("https://api.themoviedb.org/3/$path", credential, 2 * 1024 * 1024), Charsets.UTF_8))
    }
    suspend fun search(query: String, page: Int = 1): TmdbPage = withContext(Dispatchers.IO) {
        require(query.isNotBlank() && query.codePointCount(0, query.length) <= 300)
        require(page in 1..500)
        val json = request("search/movie?language=zh-CN&include_adult=false&page=$page&query=${URLEncoder.encode(query.trim(), "UTF-8")}")
        val results = json.getJSONArray("results")
        TmdbPage((0 until results.length()).map { parse(results.getJSONObject(it)) }, page, json.optInt("total_pages", 1).coerceAtMost(500))
    }
    suspend fun details(id: Long): TmdbMovie = withContext(Dispatchers.IO) {
        require(id > 0)
        parse(request("movie/$id?language=zh-CN&append_to_response=credits"))
    }
    suspend fun poster(path: String): ByteArray = withContext(Dispatchers.IO) {
        require(path.matches(Regex("/[A-Za-z0-9._-]+")))
        fetch("https://image.tmdb.org/t/p/w500$path", null, 20 * 1024 * 1024)
    }
    companion object {
        internal fun parse(j: JSONObject): TmdbMovie {
            fun text(key: String) = if (j.isNull(key)) null else j.optString(key).takeIf { it.isNotBlank() }
            val crew = j.optJSONObject("credits")?.optJSONArray("crew")
            val directors = crew?.let { (0 until it.length()).map { n -> it.getJSONObject(n) }
                .filter { it.optString("job") == "Director" }.map { it.getString("name") }.distinct().joinToString(" / ") }?.ifBlank { null }
            val countries = j.optJSONArray("production_countries")?.let { (0 until it.length()).map { n -> it.getJSONObject(n).getString("name") }.joinToString(" / ") }?.ifBlank { null }
            val id = j.getLong("id"); require(id > 0)
            val title = text("title") ?: text("original_title") ?: error("电影缺少片名")
            return TmdbMovie(id, title, text("original_title"), text("release_date"), text("poster_path"), directors,
                j.optInt("runtime").takeIf { it > 0 }, countries)
        }
        private fun download(url: String, credential: String?, limit: Int): ByteArray {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                credential?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                connection.setRequestProperty("Accept", "application/json")
                when (connection.responseCode) {
                    200 -> Unit
                    401, 403 -> error("TMDB 凭据无效或无权限，请在设置中检查；仍可手动添加")
                    429 -> error("TMDB 请求过于频繁，请稍后重试")
                    else -> error("TMDB 暂不可用，请稍后重试或手动添加")
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

data class TmdbPage(val movies: List<TmdbMovie>, val page: Int, val totalPages: Int)
data class TmdbMovie(val id: Long, val title: String, val originalTitle: String?, val releaseDate: String?,
    val posterPath: String?, val directors: String?, val runtime: Int?, val countries: String?)
