package com.seen.film.ui

import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.seen.film.data.Movie
import com.seen.film.data.MovieHistory
import com.seen.film.data.Viewing

// Debug-only fixture. No ViewModel, database, network or personal data is used.
private fun previewMovies(): List<MovieHistory> = listOf(
    Triple("完美的日子", 2023, 9), Triple("花样年华", 2000, 10),
    Triple("晒后假日", 2022, 9), Triple("海街日记", 2015, 8),
).mapIndexed { i, (title, year, rating) ->
    val id = "preview-movie-$i"
    val stamp = "2026-10-23T00:00:00Z"
    MovieHistory(
        movie = Movie(id = id, title = title, releaseYear = year, createdAt = stamp, updatedAt = stamp),
        viewings = listOf(Viewing(
            id = "preview-viewing-$i", movieId = id, viewedDate = "2026-10-${22 - i}",
            ratingHalfStars = rating, createdAt = stamp, updatedAt = stamp,
        )),
    )
}

@Composable
private fun PreviewLibrary(dark: Boolean = false, empty: Boolean = false) {
    SeenTheme(darkTheme = dark) {
        Scaffold(bottomBar = { SeenBottomBar("library", true, {}, {}, {}) }) { padding ->
            Surface(Modifier.padding(padding).consumeWindowInsets(padding)) {
            LibraryScreen(
                all = if (empty) emptyList() else previewMovies(), draft = null, canEdit = true,
                posterFile = { null },
                onAdd = {}, onMovie = {}, onResume = {},
            )
            }
        }
    }
}

@Preview(name = "电影主页 · 海报", widthDp = 390, heightDp = 844, showBackground = true, locale = "zh")
@Preview(name = "电影主页 · 200% 字号", widthDp = 390, heightDp = 844, fontScale = 2f, showBackground = true, locale = "zh")
@Composable
private fun PosterHomePreview() = PreviewLibrary()

@Preview(name = "电影主页 · 深色", widthDp = 390, heightDp = 844, showBackground = true, locale = "zh")
@Composable
private fun DarkHomePreview() = PreviewLibrary(dark = true)

@Preview(name = "电影主页 · 空状态", widthDp = 390, heightDp = 844, showBackground = true, locale = "zh")
@Composable
private fun EmptyHomePreview() = PreviewLibrary(empty = true)
