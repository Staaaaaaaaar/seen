@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seen.film.data.MovieHistory
import com.seen.film.domain.*
import java.io.File

@Composable
fun LibraryScreen(
    all: List<MovieHistory>?, draft: ViewingDraft?, canEdit: Boolean,
    initialTimeView: Boolean, posterFile: (String?) -> File?, onTimeViewChange: (Boolean) -> Unit,
    onAdd: () -> Unit, onMovie: (String) -> Unit, onResume: () -> Unit, onSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var timeView by rememberSaveable { mutableStateOf(initialTimeView) }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.RECENT.name) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val timeState = rememberLazyListState()
    val movies = remember(all, query, sortName) { libraryMovies(all.orEmpty(), query, LibrarySort.valueOf(sortName)) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Seen", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) },
            actions = {
                IconButton(onClick = { search = !search; if (!search) query = "" }) { Icon(Icons.Outlined.Search, "搜索我的电影") }
                IconButton(onClick = onAdd, enabled = canEdit && all != null) { Icon(Icons.Outlined.Add, "添加看过的电影") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "更多") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("设置与关于") }, onClick = { menu = false; onSettings() })
                    }
                }
            },
        )
        if (search) OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            label = { Text("搜索我的电影") }, singleLine = true,
            trailingIcon = { IconButton(onClick = { query = ""; search = false }) { Icon(Icons.Outlined.Close, "关闭搜索") } },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("我的电影", style = MaterialTheme.typography.titleLarge)
            Text(if (timeView) "${movies.sumOf { it.viewings.size }} 次观看" else "${movies.size} 部电影", color = SeenSecondary)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(!timeView, { timeView = false; onTimeViewChange(false) }, { Text("海报") })
            Spacer(Modifier.width(8.dp))
            FilterChip(timeView, { timeView = true; onTimeViewChange(true) }, { Text("时间") })
            Spacer(Modifier.weight(1f))
            if (!timeView) Box {
                TextButton(onClick = { sortMenu = true }) { Text(LibrarySort.valueOf(sortName).label) }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    LibrarySort.entries.forEach { sort ->
                        DropdownMenuItem(text = { Text(sort.label) }, onClick = { sortName = sort.name; sortMenu = false })
                    }
                }
            }
        }
        if (draft != null) TextButton(onClick = onResume, Modifier.padding(horizontal = 12.dp), enabled = canEdit) {
            Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("继续未完成的编辑")
        }
        when {
            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            movies.isEmpty() && query.isNotBlank() -> PageMessage("没有找到电影", "试试片名或原名", "清空搜索") { query = "" }
            movies.isEmpty() -> PageMessage("还没有电影", "添加一部看过的电影。", if (canEdit) "添加电影" else null, onAdd)
            timeView -> TimeLibrary(posterFile, movies, timeState, onMovie)
            else -> PosterLibrary(posterFile, movies, gridState, onMovie)
        }
    }
}

@Composable
private fun PosterLibrary(posterFile: (String?) -> File?, movies: List<MovieHistory>, state: LazyGridState, onMovie: (String) -> Unit) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (largeText || maxWidth < 350.dp) 1 else (maxWidth.value / 183).toInt().coerceIn(2, 5)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns), state = state, contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(movies, key = { it.movie.id }) { item ->
                Column(Modifier.clickable { onMovie(item.movie.id) }) {
                    Poster(posterFile(item.movie.posterKey), Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(item.movie.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val info = buildList {
                        item.movie.releaseYear?.let { add(it.toString()) }
                        item.latestRating()?.let { add("★ ${ViewingRules.ratingLabel(it)}") }
                        if (item.viewings.size > 1) add("${item.viewings.size} 次")
                    }
                    if (info.isNotEmpty()) Text(info.joinToString("  ·  "), fontSize = 12.sp, color = SeenSecondary)
                }
            }
        }
    }
}

@Composable
private fun TimeLibrary(posterFile: (String?) -> File?, movies: List<MovieHistory>, state: LazyListState, onMovie: (String) -> Unit) {
    val entries = remember(movies) { movies.flatMap { movie -> movie.viewings.map { movie to it } }
        .sortedWith { a, b -> viewingOrder.compare(a.second, b.second) }.groupBy { it.second.viewedDate.take(7) } }
    LazyColumn(state = state, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        entries.forEach { (month, values) ->
            item(key = "month:$month") { Text("${month.take(4)} 年 ${month.takeLast(2).toInt()} 月 · ${values.size} 次", style = MaterialTheme.typography.titleMedium) }
            items(values, key = { it.second.id }) { (history, viewing) ->
                Row(Modifier.fillMaxWidth().clickable { onMovie("${history.movie.id}/${viewing.id}") }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Poster(posterFile(history.movie.posterKey), Modifier.width(72.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(history.movie.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        history.movie.releaseYear?.let { Text(it.toString(), fontSize = 12.sp, color = SeenSecondary) }
                        Text(viewing.viewedDate, fontSize = 13.sp, color = SeenSecondary)
                        viewing.ratingHalfStars?.let { Text("★ ${ViewingRules.ratingLabel(it)}", fontSize = 13.sp) }
                        if (history.viewings.size > 1) Text("共 ${history.viewings.size} 次观看", fontSize = 12.sp, color = SeenSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun ChooseScreen(vm: SeenViewModel, all: List<MovieHistory>?, enabled: Boolean, onBack: () -> Unit, onChoose: (String?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = all.orEmpty().filter { it.movie.title.contains(query.trim(), true) || it.movie.originalTitle.orEmpty().contains(query.trim(), true) }
    Scaffold(topBar = { TopAppBar(title = { Text("添加电影") }, navigationIcon = {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("搜索已存电影") }, singleLine = true) }
            item { Button(onClick = { onChoose(null) }, Modifier.fillMaxWidth(), enabled = enabled) { Text("手动添加电影") } }
            if (matches.isNotEmpty()) item { Text("已存电影 · 选择后添加一次观看", color = SeenSecondary) }
            items(matches, key = { it.movie.id }) { history ->
                Surface(onClick = { if (enabled) onChoose(history.movie.id) }, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Poster(vm.app.posters.file(history.movie.posterKey), Modifier.width(60.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(history.movie.title, style = MaterialTheme.typography.titleMedium)
                            history.movie.releaseYear?.let { Text(it.toString(), color = SeenSecondary) }
                            Text("${history.viewings.size} 次观看", color = SeenSecondary)
                        }
                    }
                }
            }
            if (matches.isEmpty() && query.isNotBlank()) item { Text("没有匹配的本地电影，可以手动添加。", color = SeenSecondary) }
        }
    }
}
