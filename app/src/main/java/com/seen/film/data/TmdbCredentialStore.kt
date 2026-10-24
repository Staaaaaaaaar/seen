package com.seen.film.data

import android.content.Context
import com.seen.film.domain.TmdbCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TmdbCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("tmdb", Context.MODE_PRIVATE)
    fun token(): String = preferences.getString("token", "").orEmpty()
    suspend fun save(value: String) = withContext(Dispatchers.IO) {
        val normalized = value.trim()
        require(normalized.isEmpty() || TmdbCredentials.valid(normalized)) {
            "令牌格式无效，请复制完整的 API Read Access Token，不要添加 Bearer 前缀"
        }
        val editor = preferences.edit()
        if (normalized.isEmpty()) editor.remove("token") else editor.putString("token", normalized)
        check(editor.commit()) { "凭据保存失败，输入仍保留，请重试" }
    }
}
