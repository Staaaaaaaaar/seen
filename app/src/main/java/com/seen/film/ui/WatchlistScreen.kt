@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seen.film.data.MovieHistory
import java.io.File

@Composable
fun WatchlistScreen(all: List<MovieHistory>?, enabled: Boolean, posterFile: (String?) -> File?, onAdd: () -> Unit, onRecord: (String) -> Unit, onRemove: (String) -> Unit) {
    val movies = all.orEmpty().filter { it.movie.wantToWatch }.sortedByDescending { it.movie.createdAt }
    Scaffold(topBar = { TopAppBar(title = { Text("片单") }) }, floatingActionButton = {
        if (all != null) FloatingActionButton(onClick = { if (enabled) onAdd() }) { Icon(Icons.Outlined.Add, "添加想看") }
    }) { padding ->
        when {
            all == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            movies.isEmpty() -> Box(Modifier.padding(padding)) { PageMessage("还没有想看的电影", "把打算看的电影留在这里。", if (enabled) "添加想看" else null, onAdd) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp)) {
                items(movies, key = { it.movie.id }) { history ->
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(posterFile(history.movie.posterKey), Modifier.width(56.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(history.movie.title, style = MaterialTheme.typography.titleMedium)
                            history.movie.releaseYear?.let { Text(it.toString(), color = SeenSecondary) }
                            TextButton(onClick = { onRecord(history.movie.id) }, enabled = enabled) { Text("记录观看") }
                        }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = enabled) { Icon(Icons.Outlined.MoreVert, "片单操作：${history.movie.title}") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("移出片单") }, onClick = { menu = false; onRemove(history.movie.id) })
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
