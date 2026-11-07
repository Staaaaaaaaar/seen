package com.seen.film.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movies ADD COLUMN personalRatingHalfStars INTEGER")
        db.execSQL("ALTER TABLE movies ADD COLUMN overview TEXT")
        db.execSQL("ALTER TABLE movies ADD COLUMN metadataLanguage TEXT")
        db.execSQL("""UPDATE movies SET personalRatingHalfStars = (
            SELECT ratingHalfStars FROM viewings WHERE movieId = movies.id
            ORDER BY viewedDate DESC, viewedTime DESC, createdAt DESC, id ASC LIMIT 1)""")
        db.execSQL("UPDATE movies SET metadataLanguage = 'zh-CN' WHERE tmdbId IS NOT NULL")
        db.execSQL("""CREATE TABLE IF NOT EXISTS movie_notes (
            id TEXT NOT NULL, movieId TEXT NOT NULL, recordedDate TEXT NOT NULL,
            recordedTime TEXT, location TEXT, body TEXT NOT NULL,
            createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(id),
            FOREIGN KEY(movieId) REFERENCES movies(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_notes_movieId ON movie_notes(movieId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_notes_recordedDate ON movie_notes(recordedDate)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS movie_genres (
            movieId TEXT NOT NULL, genreId INTEGER NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL,
            PRIMARY KEY(movieId,genreId), FOREIGN KEY(movieId) REFERENCES movies(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS movie_credits (
            movieId TEXT NOT NULL, creditKey TEXT NOT NULL, personId INTEGER NOT NULL, name TEXT NOT NULL,
            isCast INTEGER NOT NULL, department TEXT, job TEXT, character TEXT, profilePath TEXT,
            position INTEGER NOT NULL, PRIMARY KEY(movieId,creditKey),
            FOREIGN KEY(movieId) REFERENCES movies(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS archived_posters (
            movieId TEXT NOT NULL, posterKey TEXT NOT NULL, PRIMARY KEY(movieId,posterKey),
            FOREIGN KEY(movieId) REFERENCES movies(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("INSERT INTO archived_posters(movieId,posterKey) SELECT id,posterKey FROM movies WHERE posterKey IS NOT NULL")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_metadata_state (id INTEGER NOT NULL,activeLanguage TEXT NOT NULL,generation INTEGER NOT NULL,PRIMARY KEY(id))")
        db.execSQL("INSERT INTO library_metadata_state(id,activeLanguage,generation) VALUES (1,'zh-CN',0)")
    }
}
