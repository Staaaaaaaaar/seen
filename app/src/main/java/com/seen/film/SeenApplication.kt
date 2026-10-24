package com.seen.film

import android.app.Application
import androidx.room.Room
import com.seen.film.data.*

open class SeenApplication : Application() {
    open val database by lazy { Room.databaseBuilder(this, SeenDatabase::class.java, "seen.db").build() }
    val repository by lazy { SeenRepository(database) }
    val posters by lazy { PosterStore(this) }
    val credentials by lazy { TmdbCredentialStore(this) }
    open val tmdb by lazy { TmdbRepository(token = credentials::token) }
    val drafts by lazy { DraftStore(this) }
}
