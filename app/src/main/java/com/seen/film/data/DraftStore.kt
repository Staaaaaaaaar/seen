package com.seen.film.data

import android.content.Context
import android.util.AtomicFile
import com.seen.film.domain.ViewingDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else getString(key)

class DraftStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "viewing-draft.json"))
    private val lock = Mutex()
    suspend fun read(): ViewingDraft? = withContext(Dispatchers.IO) { lock.withLock {
        if (!file.baseFile.exists()) return@withLock null
        // Read errors surface to the UI; never silently erase an unreadable draft.
        val j = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        require(j.getInt("schemaVersion") == 1) { "暂不支持此草稿版本" }
        ViewingDraft(
            key = j.getString("key"), movieId = j.getString("movieId"),
            isNewMovie = j.getBoolean("isNewMovie"), isEditing = j.getBoolean("isEditing"),
            title = j.getString("title"), originalTitle = j.optString("originalTitle", ""),
            year = j.optString("year", ""), posterKey = if (j.isNull("posterKey")) null else j.getString("posterKey"),
            tmdbId = if (j.isNull("tmdbId")) null else j.getLong("tmdbId"),
            releaseDate = j.optional("releaseDate"), directorNames = j.optional("directorNames"),
            runtimeMinutes = if (j.isNull("runtimeMinutes")) null else j.getInt("runtimeMinutes"),
            productionCountries = j.optional("productionCountries"),
            date = j.getString("date"), time = j.optString("time", ""),
            location = j.optString("location", ""),
            rating = if (j.isNull("rating")) null else j.getInt("rating"), notes = j.optString("notes", ""),
        )
    } }
    suspend fun write(draft: ViewingDraft) = withContext(Dispatchers.IO) { lock.withLock {
        val j = JSONObject().put("schemaVersion", 1)
            .put("key", draft.key).put("movieId", draft.movieId)
            .put("isNewMovie", draft.isNewMovie).put("isEditing", draft.isEditing)
            .put("title", draft.title).put("originalTitle", draft.originalTitle).put("year", draft.year)
            .put("posterKey", draft.posterKey ?: JSONObject.NULL).put("date", draft.date).put("time", draft.time)
            .put("location", draft.location).put("rating", draft.rating ?: JSONObject.NULL).put("notes", draft.notes)
        j.put("tmdbId", draft.tmdbId ?: JSONObject.NULL)
            .put("releaseDate", draft.releaseDate ?: JSONObject.NULL)
            .put("directorNames", draft.directorNames ?: JSONObject.NULL)
            .put("runtimeMinutes", draft.runtimeMinutes ?: JSONObject.NULL)
            .put("productionCountries", draft.productionCountries ?: JSONObject.NULL)
        val stream = file.startWrite()
        try { stream.write(j.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    } }
    suspend fun clear() = withContext(Dispatchers.IO) { lock.withLock { file.delete() } }
}
