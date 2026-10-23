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
