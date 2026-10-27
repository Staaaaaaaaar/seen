@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SeenApp(vm: SeenViewModel = viewModel()) = SeenTheme {
    var stack by rememberSaveable { mutableStateOf(listOf("library")) }
    val route = stack.last()
    val all by vm.library.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val restorePreview by vm.restorePreview.collectAsStateWithLifecycle()
    val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
    val pageStates = rememberSaveableStateHolder()
    var confirmExit by remember { mutableStateOf(false) }
    var confirmNew by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.persistDraft() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun push(value: String) { if (stack.last() != value) stack = stack + value }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun back() {
        if (busy) return
        if (restorePreview != null) { vm.cancelRestore { pop() }; return }
        if (route.startsWith("editor:") && draft != null) {
            if (vm.isDirty) confirmExit = true else vm.discard { pop() }
        } else if (route in listOf("settings", "watchlist")) stack = listOf("library") else pop()
    }
    BackHandler(stack.size > 1 || route in listOf("settings", "watchlist")) { back() }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it, withDismissAction = true); vm.clearError() }
    }
    LaunchedEffect(draftReady, route, draft?.key) {
        if (draftReady && route.startsWith("editor:") && draft == null && !busy) pop()
    }
    fun addRecord() {
        if (busy || !draftReady || restorePreview != null) return
        if (draft != null) confirmNew = true else push(if (route == "watchlist") "choose:watchlist" else "choose")
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (route in listOf("library", "watchlist", "settings")) SeenBottomBar(
            route = route, enabled = !busy && restorePreview == null,
            onLibrary = { stack = listOf("library") }, onWatchlist = { stack = listOf("watchlist") },
            onSettings = { stack = listOf("settings") },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                route == "library" -> pageStates.SaveableStateProvider("library") {
                    if (loadFailed) PageMessage("无法读取电影库", "请重新打开应用后重试，原有数据库不会被清空。")
                    else LibraryScreen(
                    all = all, draft = draft, canEdit = draftReady && !busy && restorePreview == null,
                    posterFile = vm.app.posters::file,
                    onAdd = ::addRecord, onMovie = { push("movie:$it") },
                    onResume = { draft?.let { push("editor:${it.key}") } })
                }
                route == "watchlist" -> pageStates.SaveableStateProvider("watchlist") {
                    if (loadFailed) PageMessage("无法读取片单", "请重新打开应用后重试。")
                    else WatchlistScreen(all, draftReady && !busy && restorePreview == null, vm.app.posters::file, ::addRecord,
                        { id -> vm.openEditor(id) { push("editor:$it") } }, vm::removeFromWatchlist)
                }
                route.startsWith("choose") -> ChooseScreen(vm, all, !busy && draftReady, { back() }, { movieId ->
                    vm.openEditor(movieId, forWatchlist = route == "choose:watchlist") { push("editor:$it") }
                }, onChooseTmdb = { push("editor:$it") }, forWatchlist = route == "choose:watchlist")
                route.startsWith("movie:") -> MovieScreen(
                    id = route.removePrefix("movie:").substringBefore("/"),
                    focusViewingId = route.substringAfter("/", "").ifBlank { null }, vm = vm, all = all,
                    onBack = { back() }, onEdit = { movieId, viewingId ->
                        vm.openEditor(movieId, viewingId) { push("editor:$it") }
                    },
                )
                route.startsWith("editor:") -> {
                    val value = draft
                    if (value == null) Box(Modifier.fillMaxSize())
                    else EditorScreen(vm, value, onBack = { back() }, onSaved = { movieId, viewingId ->
                        // Remove chooser/editor from history. A detail page never duplicates itself.
                        stack = if (value.forWatchlist) listOf("watchlist") else listOf("library", "movie:$movieId/$viewingId")
                    })
                }
                route == "settings" -> pageStates.SaveableStateProvider("settings") {
                    SettingsScreen(vm, onOpen = { push(it) })
                }
                route.startsWith("settings:") -> SettingsToolScreen(route.substringAfter(":"), vm) { back() }
            }
        }
    }
    if (confirmNew) AlertDialog(
        onDismissRequest = { confirmNew = false }, title = { Text("有未完成的编辑") },
        text = { Text("可以继续编辑，或放弃后开始新的观看记录。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirmNew = false; draft?.let { push("editor:${it.key}") } }) { Text("继续编辑") } },
        dismissButton = { Column {
            TextButton(enabled = !busy, onClick = { vm.discard { confirmNew = false; push(if (stack.first() == "watchlist") "choose:watchlist" else "choose") } }) { Text("放弃后新建", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { confirmNew = false }) { Text("取消") }
        } },
    )
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false }, title = { Text("放弃未保存的修改？") },
        text = { Text("退出会丢弃当前编辑的内容。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            vm.discard { confirmExit = false; pop() }
        }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("继续编辑") } },
    )
}
