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
    @ColumnInfo(defaultValue = "0") val wantToWatch: Boolean = false,
    val personalRatingHalfStars: Int? = null,
    val overview: String? = null,
    val metadataLanguage: String? = null,
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
    @Relation(parentColumn = "id", entityColumn = "movieId") val notes: List<MovieNote> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "movieId") val genres: List<MovieGenre> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "movieId") val credits: List<MovieCredit> = emptyList(),
)

@Dao
interface SeenDao {
    @Transaction @Query("SELECT * FROM movies")
    fun observeLibrary(): Flow<List<MovieHistory>>
    @Transaction @Query("SELECT * FROM movies") suspend fun allHistories(): List<MovieHistory>
    @Query("SELECT COUNT(*) + (SELECT COUNT(*) FROM viewings) + (SELECT COUNT(*) FROM movie_notes) + (SELECT COUNT(*) FROM movie_genres) + (SELECT COUNT(*) FROM movie_credits) + COALESCE((SELECT generation FROM library_metadata_state WHERE id=1),0) FROM movies")
    fun observeLibraryChanges(): kotlinx.coroutines.flow.Flow<Long>
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
    @Query("DELETE FROM viewings WHERE movieId = :movieId") suspend fun deleteMovieViewings(movieId: String)
    @Query("DELETE FROM movies WHERE id = :id") suspend fun deleteMovie(id: String): Int
    @Query("UPDATE movies SET wantToWatch = :wanted WHERE id = :id") suspend fun setWanted(id: String, wanted: Boolean): Int
    @Query("SELECT (SELECT COALESCE(SUM(length(CAST(body || COALESCE(location,'') AS BLOB))),0) FROM movie_notes) + (SELECT COALESCE(SUM(length(CAST(COALESCE(overview,'') AS BLOB))),0) FROM movies) + (SELECT COALESCE(SUM(length(CAST(name AS BLOB))),0) FROM movie_genres) + (SELECT COALESCE(SUM(length(CAST(name || COALESCE(character,'') || COALESCE(job,'') || COALESCE(department,'') AS BLOB))),0) FROM movie_credits)") suspend fun extraTextBytes(): Long
    @Query("SELECT * FROM movie_notes WHERE id = :id") suspend fun note(id: String): MovieNote?
    @Query("SELECT COUNT(*) FROM movie_notes") suspend fun noteCount(): Int
    @Query("SELECT * FROM movie_notes LIMIT 100001") suspend fun allNotes(): List<MovieNote>
    @Query("SELECT * FROM movie_genres LIMIT 100001") suspend fun allGenres(): List<MovieGenre>
    @Query("SELECT * FROM movie_credits LIMIT 100001") suspend fun allCredits(): List<MovieCredit>
    @Query("SELECT posterKey FROM archived_posters") suspend fun archivedPosterKeys(): List<String>
    @Query("UPDATE movies SET title=:title,originalTitle=:originalTitle,releaseYear=:year,releaseDate=:date,directorNames=:directors,runtimeMinutes=:runtime,productionCountries=:countries,overview=:overview,posterKey=:poster,metadataLanguage=:language,updatedAt=:updated WHERE id=:id")
    suspend fun updateLocalizedMovie(id: String, title: String, originalTitle: String?, year: Int?, date: String?, directors: String?, runtime: Int?, countries: String?, overview: String?, poster: String?, language: String, updated: String): Int
    @Query("SELECT * FROM archived_posters LIMIT 10001") suspend fun allArchivedPosters(): List<ArchivedPoster>
    @Query("SELECT * FROM library_metadata_state WHERE id=1") fun observeMetadataState(): kotlinx.coroutines.flow.Flow<LibraryMetadataState?>
    @Query("SELECT * FROM library_metadata_state WHERE id=1") suspend fun metadataState(): LibraryMetadataState?
    @Query("DELETE FROM movie_notes") suspend fun deleteAllNotes()
    @Query("DELETE FROM movie_genres") suspend fun deleteAllGenres()
    @Query("DELETE FROM movie_credits") suspend fun deleteAllCredits()
    @Query("DELETE FROM archived_posters") suspend fun deleteAllArchivedPosters()
    @Query("DELETE FROM movie_notes WHERE movieId=:id") suspend fun deleteMovieNotes(id: String)
    @Query("DELETE FROM movie_genres WHERE movieId=:id") suspend fun deleteMovieGenres(id: String)
    @Query("DELETE FROM movie_credits WHERE movieId=:id") suspend fun deleteMovieCredits(id: String)
    @Query("DELETE FROM archived_posters WHERE movieId=:id") suspend fun deleteMovieArchivedPosters(id: String)
    @Query("UPDATE movies SET personalRatingHalfStars=:rating,updatedAt=:updated WHERE id=:id") suspend fun setRating(id: String,rating: Int?,updated: String): Int
    @Insert suspend fun insertNote(note: MovieNote)
    @Update suspend fun updateNote(note: MovieNote): Int
    @Query("DELETE FROM movie_notes WHERE id=:id") suspend fun deleteNote(id: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGenres(items: List<MovieGenre>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCredits(items: List<MovieCredit>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertArchivedPosters(items: List<ArchivedPoster>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMetadataState(state: LibraryMetadataState)
    @Insert suspend fun insertMovie(movie: Movie)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertViewing(viewing: Viewing)
    @Update suspend fun updateViewing(viewing: Viewing): Int
    @Query("DELETE FROM viewings WHERE id = :id") suspend fun deleteViewing(id: String): Int
}

@Database(entities = [Movie::class, Viewing::class, MovieNote::class, MovieGenre::class, MovieCredit::class, ArchivedPoster::class, LibraryMetadataState::class], version = 3, exportSchema = true)
abstract class SeenDatabase : RoomDatabase() {
    abstract fun dao(): SeenDao
    companion object {
        val MIGRATION_2_3 = com.seen.film.data.MIGRATION_2_3
        val MIGRATIONS get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN wantToWatch INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
