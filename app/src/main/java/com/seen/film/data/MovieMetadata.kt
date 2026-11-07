package com.seen.film.data

import androidx.room.*

@Entity(tableName = "movie_notes", foreignKeys = [ForeignKey(entity = Movie::class,
    parentColumns = ["id"], childColumns = ["movieId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("movieId"), Index("recordedDate")])
data class MovieNote(
    @PrimaryKey val id: String,
    val movieId: String,
    val recordedDate: String,
    val recordedTime: String? = null,
    val location: String? = null,
    val body: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "movie_genres", primaryKeys = ["movieId", "genreId"],
    foreignKeys = [ForeignKey(entity = Movie::class, parentColumns = ["id"], childColumns = ["movieId"], onDelete = ForeignKey.RESTRICT)])
data class MovieGenre(val movieId: String, val genreId: Long, val name: String, val position: Int)

@Entity(tableName = "movie_credits", primaryKeys = ["movieId", "creditKey"],
    foreignKeys = [ForeignKey(entity = Movie::class, parentColumns = ["id"], childColumns = ["movieId"], onDelete = ForeignKey.RESTRICT)])
data class MovieCredit(
    val movieId: String, val creditKey: String, val personId: Long, val name: String,
    val isCast: Boolean, val department: String? = null, val job: String? = null,
    val character: String? = null, val profilePath: String? = null, val position: Int,
)

@Entity(tableName = "archived_posters", primaryKeys = ["movieId", "posterKey"],
    foreignKeys = [ForeignKey(entity = Movie::class, parentColumns = ["id"], childColumns = ["movieId"], onDelete = ForeignKey.RESTRICT)])
data class ArchivedPoster(val movieId: String, val posterKey: String)

@Entity(tableName = "library_metadata_state")
data class LibraryMetadataState(@PrimaryKey val id: Int = 1, val activeLanguage: String = "zh-CN", val generation: Long = 0)
