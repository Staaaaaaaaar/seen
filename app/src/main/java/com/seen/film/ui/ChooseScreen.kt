@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.seen.film.data.MovieHistory

@Composable
fun ChooseScreen(vm: SeenViewModel, all: List<MovieHistory>?, enabled: Boolean, onBack: () -> Unit, onChoose: (String?) -> Unit, onChooseTmdb: (String) -> Unit, forWatchlist: Boolean = false) {
    var query by rememberSaveable { mutableStateOf("") }
    DisposableEffect(vm) { onDispose { vm.clearSearch() } }
    val busy by vm.busy.collectAsState()
    val remote by vm.search.collectAsState()
    val searching by vm.searching.collectAsState()
    val message by vm.searchMessage.collectAsState()
    val matches = remember(all, query) {
        val items = all.orEmpty().filter { it.movie.title.contains(query.trim(), true) || it.movie.originalTitle.orEmpty().contains(query.trim(), true) }
        if (query.isBlank()) items.sortedByDescending { it.movie.updatedAt }.take(5) else items
    }
    val canSearch = enabled && !searching && query.isNotBlank() && query.codePointCount(0, query.length) <= 300
    Scaffold(topBar = { TopAppBar(title = { Text("选择电影") }, navigationIcon = {
        IconButton(onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }) { padding ->
        LazyColumn(Modifier.padding(padding).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                OutlinedTextField(query, { query = it; vm.clearSearch() }, Modifier.fillMaxWidth(), label = { Text("搜索片名或原名") }, singleLine = true,
                    enabled = enabled, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (canSearch) vm.searchTmdb(query) }))
            }
            if (matches.isNotEmpty()) item { Text(if (query.isBlank()) "最近收录" else "已存电影", style = MaterialTheme.typography.titleMedium) }
            items(matches, key = { "local:${it.movie.id}" }) { history ->
                Surface(onClick = { onChoose(history.movie.id) }, enabled = enabled, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(vm.app.posters.file(history.movie.posterKey), Modifier.width(52.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(history.movie.title, style = MaterialTheme.typography.titleMedium)
                            history.movie.releaseYear?.let { Text(it.toString(), color = SeenSecondary) }
                            Text("${history.viewings.size} 次观看，可再次记录", style = MaterialTheme.typography.bodySmall, color = SeenSecondary)
                        }
                        Icon(Icons.AutoMirrored.Outlined.NavigateNext, null)
                    }
                }
            }
            item { Button(onClick = { vm.searchTmdb(query) }, enabled = canSearch, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("搜索 TMDB") } }
            item { TextButton(onClick = { onChoose(null) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("手动添加电影") } }
            if (searching) item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Text("正在搜索电影…", color = SeenSecondary) } }
            if (busy) item { Text("正在获取电影资料…", color = SeenSecondary) }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            remote?.let { page ->
                item { Text("TMDB · 第 ${page.page} 页 · 资料来源 The Movie Database", style = MaterialTheme.typography.bodySmall, color = SeenSecondary) }
                if (page.movies.isEmpty()) item { Text("没有找到电影，可修改关键词或手动添加。") }
                items(page.movies, key = { "tmdb:${it.id}" }) { movie ->
                    Surface(onClick = { vm.chooseTmdb(movie.id, forWatchlist) { onChooseTmdb(it) } }, enabled = enabled, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(movie.title, style = MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(movie.originalTitle, movie.releaseDate?.take(4)).joinToString(" / "), color = SeenSecondary)
                                if (all.orEmpty().any { it.movie.tmdbId == movie.id }) Text("已收录，可再次记录", style = MaterialTheme.typography.bodySmall, color = SeenSecondary)
                            }
                            Icon(Icons.AutoMirrored.Outlined.NavigateNext, null)
                        }
                    }
                    HorizontalDivider()
                }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page.page > 1) TextButton(onClick = { vm.searchTmdb(query, page.page - 1) }, enabled = canSearch) { Text("上一页") }
                    if (page.page < page.totalPages) TextButton(onClick = { vm.searchTmdb(query, page.page + 1) }, enabled = canSearch) { Text("下一页") }
                } }
            }
        }
    }
}
