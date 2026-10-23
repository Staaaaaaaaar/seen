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
import androidx.compose.ui.graphics.Color
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
                    if (index >= 0) listState.scrollToItem(index + 3)
                }
                LazyColumn(Modifier.padding(padding).testTag("movie-detail-list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item {
                        BoxWithConstraints {
                            if (maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
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
                    item { Button(onClick = { onEdit(movie.id, null) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("再看一次")
                    } }
                    item { Text("观看记录 · ${viewings.size} 次", style = MaterialTheme.typography.titleLarge) }
                    items(viewings, key = { it.id }) { viewing ->
                        var menu by remember { mutableStateOf(false) }
                        Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    if (expanded == viewing.id) {
                                        HorizontalDivider()
                                        SelectionContainer { Text(viewing.notes, style = MaterialTheme.typography.bodyLarge) }
                                    }
                                }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(history.movie.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        history.movie.originalTitle?.let { Text(it, color = SeenSecondary) }
        history.movie.releaseYear?.let { Text(it.toString(), color = SeenSecondary) }
        Text("看过 ${history.viewings.size} 次", fontWeight = FontWeight.Medium)
    }
}
