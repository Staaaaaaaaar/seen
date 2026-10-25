@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seen.film.data.MovieHistory
import com.seen.film.domain.*
import java.io.File

@Composable
fun LibraryScreen(
    all: List<MovieHistory>?, draft: ViewingDraft?, canEdit: Boolean,
    posterFile: (String?) -> File?, onAdd: () -> Unit, onMovie: (String) -> Unit, onResume: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.RECENT.name) }
    var sortMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val movies = remember(all, query, sortName) { libraryMovies(all.orEmpty(), query, LibrarySort.valueOf(sortName)) }
    BackHandler(search) { query = ""; search = false }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (search) TextField(
                    query, { query = it }, Modifier.fillMaxWidth().testTag("library-search").semantics { contentDescription = "搜索我的电影" },
                    placeholder = { Text("搜索我的电影") }, singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    trailingIcon = { IconButton(onClick = { query = ""; search = false }) { Icon(Icons.Outlined.Close, "关闭搜索") } },
                ) else Text("电影库", style = MaterialTheme.typography.titleLarge)
            },
            actions = {
                if (!search) IconButton(onClick = { search = true }) { Icon(Icons.Outlined.Search, "搜索我的电影") }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, "排序电影") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        Text("${movies.size} 部电影", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = SeenSecondary)
                        LibrarySort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                leadingIcon = { if (sortName == sort.name) Icon(Icons.Outlined.Check, null) },
                                modifier = Modifier.semantics { selected = sortName == sort.name },
                                onClick = { sortName = sort.name; sortMenu = false },
                            )
                        }
                    }
                }
            },
        )
        if (draft != null) TextButton(onClick = onResume, modifier = Modifier.padding(horizontal = 12.dp), enabled = canEdit) {
            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("继续编辑${draft.title.takeIf { it.isNotBlank() }?.let { "《$it》" }.orEmpty()}", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when {
            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            movies.isEmpty() && query.isNotBlank() -> PageMessage("没有找到电影", "试试片名或原名", "清空搜索") { query = "" }
            movies.isEmpty() -> PageMessage("还没有电影", "添加一部看过的电影。", if (canEdit) "添加记录" else null, onAdd)
            else -> {
                val largeText = LocalDensity.current.fontScale >= 1.5f
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val columns = if (largeText || maxWidth < 350.dp) 1 else (maxWidth.value / 183).toInt().coerceIn(2, 5)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns), state = gridState,
                        modifier = Modifier.testTag("library-grid"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        items(movies, key = { it.movie.id }) { item ->
                            Column(Modifier.clickable(onClickLabel = "查看电影与观看历史") { onMovie(item.movie.id) }.semantics(mergeDescendants = true) {}) {
                                Poster(posterFile(item.movie.posterKey), Modifier.fillMaxWidth())
                                Spacer(Modifier.height(10.dp))
                                Text(item.movie.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val meta = buildList {
                                    item.movie.releaseYear?.let { add(it.toString()) }
                                    item.latestRating()?.let { add("个人评分 ${ViewingRules.ratingLabel(it)}") }
                                }
                                if (meta.isNotEmpty()) Text(meta.joinToString("  /  "), style = MaterialTheme.typography.bodySmall, color = SeenSecondary)
                                if (item.viewings.size > 1) Text("${item.viewings.size} 次观看", style = MaterialTheme.typography.bodySmall, color = SeenSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
