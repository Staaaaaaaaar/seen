@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seen.film.data.MovieHistory
import com.seen.film.data.Viewing
import com.seen.film.domain.ViewingRules
import com.seen.film.domain.sortedViewings

@Composable
fun MovieScreen(id: String, focusViewingId: String?, vm: SeenViewModel, all: List<MovieHistory>?, onBack: () -> Unit, onEdit: (String, String?) -> Unit) {
    val history = all?.firstOrNull { it.movie.id == id }
    val busy by vm.busy.collectAsState()
    var expanded by rememberSaveable(id, focusViewingId) { mutableStateOf(focusViewingId) }
    var deleting by remember { mutableStateOf<Viewing?>(null) }
    val listState = rememberLazyListState()
    Scaffold(topBar = { TopAppBar(title = { Text("电影详情") }, navigationIcon = {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }) { padding ->
        when {
            all == null -> Box(Modifier.padding(padding)) { CircularProgressIndicator() }
            history == null -> Box(Modifier.padding(padding)) { PageMessage("电影已不存在", "返回电影库查看其他电影。", "返回", onBack) }
            else -> {
                val movie = history.movie
                val viewings = history.sortedViewings()
                LaunchedEffect(focusViewingId, viewings.size) {
                    val index = viewings.indexOfFirst { it.id == focusViewingId }
                    if (index >= 0) listState.scrollToItem(if (index == 0) 2 else index + 3)
                }
                LazyColumn(Modifier.padding(padding).testTag("movie-detail-list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item {
                        BoxWithConstraints {
                            if (maxWidth < 300.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Poster(vm.app.posters.file(movie.posterKey), Modifier.width(136.dp))
                                    FilmInfo(history)
                                }
                            } else Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Poster(vm.app.posters.file(movie.posterKey), Modifier.width(124.dp))
                                Column(Modifier.weight(1f)) { FilmInfo(history) }
                            }
                        }
                    }
                    item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                    item {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("观看记录 · ${viewings.size} 次", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { onEdit(movie.id, null) }, enabled = !busy) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("再看一次")
                            }
                        }
                    }
                    items(viewings, key = { it.id }) { viewing ->
                        var menu by remember { mutableStateOf(false) }
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(viewing.viewedDate, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Box {
                                        IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Outlined.MoreHoriz, "${viewing.viewedDate} 观看记录操作") }
                                        DropdownMenu(menu, { menu = false }) {
                                            DropdownMenuItem(text = { Text("编辑观看记录") }, onClick = { menu = false; onEdit(movie.id, viewing.id) })
                                            DropdownMenuItem(text = { Text("删除本次观看", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = viewing })
                                        }
                                    }
                                }
                                val meta = listOfNotNull(viewing.location, viewing.viewedTime)
                                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = SeenSecondary)
                                viewing.ratingHalfStars?.let { Text("个人评分  ★ ${ViewingRules.ratingLabel(it)} / 5") }
                                if (viewing.notes != null) {
                                    TextButton(onClick = { expanded = if (expanded == viewing.id) null else viewing.id }) {
                                        Text(if (expanded == viewing.id) "收起感受" else "感受")
                                    }
                                    SelectionContainer {
                                        Text(viewing.notes, style = MaterialTheme.typography.bodyLarge,
                                            maxLines = if (expanded == viewing.id) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                    if (viewings.isEmpty()) item { Text("暂无观看记录", color = SeenSecondary) }
                }
            }
        }
    }
    deleting?.let { viewing ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这次观看？") },
            text = { Text("${history?.movie?.title.orEmpty()} · ${viewing.viewedDate}\n仅删除这次观看记录，其他观看历史不受影响。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { vm.deleteViewing(viewing.id); deleting = null }) { Text("删除记录", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun FilmInfo(history: MovieHistory) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(history.movie.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        history.movie.originalTitle?.let { Text(it, color = SeenSecondary) }
        val meta = listOfNotNull(history.movie.releaseYear?.toString(), history.movie.runtimeMinutes?.let { "$it 分钟" })
        if (meta.isNotEmpty()) Text(meta.joinToString(" / "), color = SeenSecondary)
        history.movie.directorNames?.let { Text("导演 · $it", color = SeenSecondary) }
        var more by rememberSaveable(history.movie.id) { mutableStateOf(false) }
        if (history.movie.productionCountries != null) {
            TextButton(onClick = { more = !more }) { Text(if (more) "收起资料" else "更多资料") }
            if (more) Text(history.movie.productionCountries, color = SeenSecondary)
        }
        history.movie.tmdbId?.let { id ->
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri("https://www.themoviedb.org/movie/$id") }) { Text("资料来源 · TMDB") }
        }
        Text("看过 ${history.viewings.size} 次", fontWeight = FontWeight.Medium)
    }
}
