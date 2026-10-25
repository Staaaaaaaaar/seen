package com.seen.film.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "movies", indices = [Index(value = ["tmdbId"], unique = true)])
data class Movie(
    @PrimaryKey val id: String,
    val title: String,
    val originalTitle: String? = null,
    val releaseYear: Int? = null,
    val releaseDate: String? = null,
    val tmdbId: Long? = null,
    val posterKey: String? = null,
    val source: String = "MANUAL",
    val directorNames: String? = null,
    val runtimeMinutes: Int? = null,
    val productionCountries: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "viewings",
    foreignKeys = [ForeignKey(
        entity = Movie::class, parentColumns = ["id"], childColumns = ["movieId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("movieId"), Index("viewedDate")],
)
data class Viewing(
    @PrimaryKey val id: String,
    val movieId: String,
    val viewedDate: String,
    val viewedTime: String? = null,
    val location: String? = null,
    val ratingHalfStars: Int? = null,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

data class MovieHistory(
    @Embedded val movie: Movie,
    @Relation(parentColumn = "id", entityColumn = "movieId") val viewings: List<Viewing>,
)

@Dao
interface SeenDao {
    @Transaction @Query("SELECT * FROM movies")
    fun observeLibrary(): Flow<List<MovieHistory>>
    @Query("SELECT * FROM movies WHERE id = :id") suspend fun movie(id: String): Movie?
    @Query("SELECT * FROM movies WHERE tmdbId = :id") suspend fun movieByTmdbId(id: Long): Movie?
    @Query("SELECT * FROM viewings WHERE id = :id") suspend fun viewing(id: String): Viewing?
    @Query("SELECT COALESCE(SUM(length(CAST(title || COALESCE(originalTitle,'') || COALESCE(directorNames,'') || COALESCE(productionCountries,'') AS BLOB))),0) FROM movies")
    suspend fun movieTextBytes(): Long
    @Query("SELECT COALESCE(SUM(length(CAST(COALESCE(notes,'') || COALESCE(location,'') AS BLOB))),0) FROM viewings")
    suspend fun viewingTextBytes(): Long
    @Query("SELECT posterKey FROM movies WHERE posterKey IS NOT NULL") suspend fun posterKeys(): List<String>
    @Query("SELECT COUNT(*) FROM movies") suspend fun movieCount(): Int
    @Query("SELECT COUNT(*) FROM viewings") suspend fun viewingCount(): Int
    @Query("SELECT * FROM movies LIMIT 10001") suspend fun allMovies(): List<Movie>
    @Query("SELECT * FROM viewings LIMIT 100001") suspend fun allViewings(): List<Viewing>
    @Query("DELETE FROM viewings") suspend fun deleteAllViewings()
    @Query("DELETE FROM movies") suspend fun deleteAllMovies()
    @Insert suspend fun insertMovie(movie: Movie)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertViewing(viewing: Viewing)
    @Update suspend fun updateViewing(viewing: Viewing): Int
    @Query("DELETE FROM viewings WHERE id = :id") suspend fun deleteViewing(id: String): Int
}

@Database(entities = [Movie::class, Viewing::class], version = 1, exportSchema = true)
abstract class SeenDatabase : RoomDatabase() {
    abstract fun dao(): SeenDao
}
