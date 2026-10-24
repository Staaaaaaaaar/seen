@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seen.film.BuildConfig

@Composable
fun SeenApp(vm: SeenViewModel = viewModel()) = SeenTheme {
    var stack by rememberSaveable { mutableStateOf(listOf("library")) }
    val route = stack.last()
    val all by vm.library.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
    val pageStates = rememberSaveableStateHolder()
    var confirmExit by remember { mutableStateOf(false) }
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
        if (route.startsWith("editor:") && draft != null) {
            if (vm.isDirty) confirmExit = true else vm.discard { pop() }
        } else pop()
    }
    BackHandler(stack.size > 1) { back() }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it, withDismissAction = true); vm.clearError() }
    }
    LaunchedEffect(draftReady, route, draft?.key) {
        if (draftReady && route.startsWith("editor:") && draft == null && !busy) pop()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                route == "library" -> pageStates.SaveableStateProvider("library") {
                    if (loadFailed) PageMessage("无法读取电影库", "请重新打开应用后重试，原有数据库不会被清空。")
                    else LibraryScreen(
                    all = all, draft = draft, canEdit = draftReady && !busy,
                    initialTimeView = vm.timeView, posterFile = vm.app.posters::file, onTimeViewChange = vm::setTimeView,
                    onAdd = { push("choose") }, onMovie = { push("movie:$it") },
                    onResume = { draft?.let { push("editor:${it.key}") } }, onSettings = { push("settings") })
                }
                route == "choose" -> ChooseScreen(vm, all, !busy && draftReady, { back() }, { movieId ->
                    vm.openEditor(movieId) { push("editor:$it") }
                }, onChooseTmdb = { push("editor:$it") })
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
                    else EditorScreen(vm, value, onBack = { back() }, onSaved = { movieId, _ ->
                        // Remove chooser/editor from history. A detail page never duplicates itself.
                        stack = listOf("library", "movie:$movieId")
                    })
                }
                route == "settings" -> SettingsScreen(vm) { back() }
            }
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false }, title = { Text("放弃未保存的修改？") },
        text = { Text("退出会丢弃当前编辑的内容。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            vm.discard { confirmExit = false; pop() }
        }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("继续编辑") } },
    )
}

@Composable
private fun SettingsScreen(vm: SeenViewModel, onBack: () -> Unit) {
    val configured by vm.credentialConfigured.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf("") }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("设置") }, navigationIcon = {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("数据", style = MaterialTheme.typography.titleMedium)
            Text("电影与观看记录仅保存在本机。无账号、无云同步。", color = SeenSecondary)
            HorizontalDivider()
            Text("电影资料 · TMDB", style = MaterialTheme.typography.titleMedium)
            Text(if (configured) "已配置访问凭据" else "未配置；可继续手动记录", color = SeenSecondary)
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("API Read Access Token") }, enabled = !busy,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Row {
                TextButton(onClick = {
                    vm.saveCredential(token) { token = "" }
                }, enabled = !busy && token.isNotBlank()) { Text("保存凭据") }
                TextButton(enabled = !busy, onClick = { vm.saveCredential("") { token = "" } }) { Text("移除凭据") }
            }
            Text("凭据仅保存在本机私有存储。搜索会向 TMDB 发送关键词；已保存资料和海报可离线使用。", color = SeenSecondary)
            TextButton(onClick = { uriHandler.openUri("https://www.themoviedb.org/settings/api") }) { Text("获取 TMDB 凭据") }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.seen.film.R.drawable.tmdb_logo),
                contentDescription = "The Movie Database (TMDB)", modifier = Modifier.width(93.dp).height(67.dp))
            Text("This product uses the TMDB API but is not endorsed or certified by TMDB.")
            TextButton(onClick = { uriHandler.openUri("https://www.themoviedb.org") }) { Text("The Movie Database · TMDB") }
            HorizontalDivider()
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Text("浅色", color = SeenSecondary)
            HorizontalDivider()
            Text("Seen", style = MaterialTheme.typography.headlineMedium)
            Text("Personal Film Journal", color = SeenSecondary)
            Text("版本 ${BuildConfig.VERSION_NAME}", color = SeenSecondary)
        }
    }
}
