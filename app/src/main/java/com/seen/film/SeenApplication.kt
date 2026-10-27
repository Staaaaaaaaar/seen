package com.seen.film

import android.app.Application
import androidx.room.Room
import com.seen.film.data.*

open class SeenApplication : Application() {
    open val database by lazy { Room.databaseBuilder(this, SeenDatabase::class.java, "seen.db").addMigrations(SeenDatabase.MIGRATION_1_2).build() }
    val libraryAccess = LibraryAccess()
    val repository by lazy { SeenRepository(database, libraryAccess) }
    val posters by lazy { PosterStore(this) }
    val credentials by lazy { TmdbCredentialStore(this) }
    open val tmdb by lazy { TmdbRepository(token = credentials::token) }
    val backups by lazy { BackupRepository(this, database, posters, drafts, libraryAccess) }
    val drafts by lazy { DraftStore(this, libraryAccess) }
}
