@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seen.film.BuildConfig

@Composable
fun SeenBottomBar(route: String, enabled: Boolean, onLibrary: () -> Unit, onWatchlist: () -> Unit, onSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                BottomDestination("记录", Icons.Outlined.GridView, route == "library", enabled, onLibrary)
                BottomDestination("片单", Icons.Outlined.BookmarkBorder, route == "watchlist", enabled, onWatchlist)
                BottomDestination("设置", Icons.Outlined.Tune, route == "settings", enabled, onSettings)
            }
        }
    }
}

@Composable
private fun RowScope.BottomDestination(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).testTag("tab-$label").clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(23.dp), tint = if (selected) MaterialTheme.colorScheme.onSurface else SeenSecondary)
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onSurface else SeenSecondary)
    }
}

@Composable
fun SettingsScreen(vm: SeenViewModel, onOpen: (String) -> Unit) {
    val configured by vm.credentialConfigured.collectAsState()
    val busy by vm.busy.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置", style = MaterialTheme.typography.titleLarge) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 32.dp)) {
            SettingsHeading("电影资料")
            SettingsRow("TMDB 凭证", if (configured) "已配置，可修改或移除" else "未配置；可继续手动记录", Icons.Outlined.Key, !busy) { onOpen("settings:credentials") }
            Spacer(Modifier.height(24.dp))
            SettingsHeading("数据管理")
            SettingsRow("导出数据", "全部电影、观看记录与海报", Icons.Outlined.FileUpload, !busy) { onOpen("settings:export") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow("导入备份", "预检后确认覆盖本机数据", Icons.Outlined.FileDownload, !busy) { onOpen("settings:import") }
            Spacer(Modifier.height(24.dp))
            SettingsHeading("关于")
            SettingsRow("关于 Seen", "版本 ${BuildConfig.VERSION_NAME} 与资料来源", Icons.Outlined.Info, !busy) { onOpen("settings:about") }
        }
    }
}

@Composable
private fun SettingsHeading(label: String) {
    Text(label, Modifier.padding(bottom = 8.dp).semantics { heading() }, style = MaterialTheme.typography.bodySmall, color = SeenSecondary)
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, onClick = onClick).padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = SeenSecondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SeenSecondary)
        }
        Icon(Icons.AutoMirrored.Outlined.NavigateNext, null, tint = SeenSecondary)
    }
}

@Composable
fun SettingsToolScreen(tool: String, vm: SeenViewModel, onBack: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val title = when (tool) { "credentials" -> "TMDB 凭证"; "export" -> "导出数据"; "import" -> "导入备份"; else -> "关于 Seen" }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (tool) {
                "credentials" -> CredentialControls(vm)
                "export" -> BackupControls(vm, allowImport = false)
                "import" -> BackupControls(vm, allowExport = false)
                else -> {
                    Text("Seen", style = MaterialTheme.typography.headlineSmall)
                    Text("个人的已看电影库", color = SeenSecondary)
                    Text("版本 ${BuildConfig.VERSION_NAME}")
                    Text("电影与观看记录保存在本机，无账号、广告或遥测。可选连接 TMDB 获取电影资料。", color = SeenSecondary)
                    HorizontalDivider()
                    TmdbAttribution()
                }
            }
        }
    }
}

@Composable
private fun CredentialControls(vm: SeenViewModel) {
    val configured by vm.credentialConfigured.collectAsState()
    val busy by vm.busy.collectAsState()
    // Credentials stay out of the Activity saved-state Bundle.
    var token by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    Text(if (configured) "已配置访问凭据" else "未配置；可继续手动记录", color = SeenSecondary)
    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("API Read Access Token") }, enabled = !busy,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
    Button(onClick = { vm.saveCredential(token) { token = "" } }, enabled = !busy && token.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("保存凭据") }
    TextButton(enabled = !busy, onClick = { vm.saveCredential("") { token = "" } }) { Text("移除凭据") }
    Text("凭据仅保存在本机私有存储。搜索会向 TMDB 发送关键词；已保存资料和海报可离线使用。", color = SeenSecondary)
    TextButton(onClick = { uriHandler.openUri("https://www.themoviedb.org/settings/api") }) { Text("获取 TMDB 凭据") }
    HorizontalDivider()
    TmdbAttribution()
}

@Composable
private fun TmdbAttribution() {
    val uriHandler = LocalUriHandler.current
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.seen.film.R.drawable.tmdb_logo),
        contentDescription = "The Movie Database (TMDB)", modifier = Modifier.width(93.dp).height(67.dp))
    Text("This product uses the TMDB API but is not endorsed or certified by TMDB.")
    TextButton(onClick = { uriHandler.openUri("https://www.themoviedb.org") }) { Text("The Movie Database · TMDB") }
}
