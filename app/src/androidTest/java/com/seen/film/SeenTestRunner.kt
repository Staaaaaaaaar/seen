package com.seen.film

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.runner.AndroidJUnitRunner
import com.seen.film.data.SeenDatabase
import java.io.File
import java.util.UUID

/** All instrumentation runs use an isolated database/files/preferences. */
class SeenTestApplication : SeenApplication() {
    private val session = UUID.randomUUID().toString()
    private val testRoot by lazy { File(super.getCacheDir(), "instrumentation-$session").apply { mkdirs() } }
    override val tmdb by lazy {
        com.seen.film.data.TmdbRepository(
            token = { getSharedPreferences("tmdb", 0).getString("token", "").orEmpty() },
            fetch = { url, _, _ ->
                if (url.contains("timeout")) throw java.net.SocketTimeoutException("fixture timeout")
                if (url.contains("query=authfailure")) error("TMDB 凭据无效或无权限，请在设置中检查；仍可手动添加")
                when {
                    url.contains("image.tmdb.org") -> throw java.net.SocketTimeoutException("fixture poster timeout")
                    url.contains("query=empty") -> """{"total_pages":0,"results":[]}""".toByteArray()
                    url.contains("query=posterfailure") -> """{"total_pages":1,"results":[{"id":4243,"title":"海报失败验收电影","release_date":"2002-01-01"}]}""".toByteArray()
                    url.contains("search/movie") && url.contains("page=2") -> """{"total_pages":2,"results":[{"id":4244,"title":"第二页电影","release_date":"2003-01-01"}]}""".toByteArray()
                    url.contains("movie/4243") -> """{"id":4243,"title":"海报失败验收电影","release_date":"2002-01-01","poster_path":"/fixture.jpg"}""".toByteArray()
                    url.contains("search/movie") -> """{"page":1,"total_pages":2,"results":[{"id":4242,"title":"联网验收电影","original_title":"Fixture Film","release_date":"2001-01-01"}]}""".toByteArray()
                    url.contains("movie/4242") -> """{"id":4242,"title":"联网验收电影","original_title":"Fixture Film","release_date":"2001-01-01","runtime":100,"credits":{"crew":[{"job":"Director","name":"测试导演"}]}}""".toByteArray()
                    else -> error("Unexpected fixture URL")
                }
            },
        )
    }
    override val database by lazy { Room.inMemoryDatabaseBuilder(this, SeenDatabase::class.java).build() }
    override fun getFilesDir(): File = File(testRoot, "files").apply { mkdirs() }
    override fun getCacheDir(): File = File(testRoot, "cache").apply { mkdirs() }
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("instrumentation-$session-$name", mode)
}

class SeenTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, SeenTestApplication::class.java.name, context)
}
