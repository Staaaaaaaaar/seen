package com.seen.film.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupControls(vm: SeenViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ready by vm.draftReady.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val preview by vm.restorePreview.collectAsStateWithLifecycle()
    val prepared by vm.preparedExport.collectAsStateWithLifecycle()
    val message by vm.backupMessage.collectAsStateWithLifecycle()
    val feedback = remember { BringIntoViewRequester() }
    LaunchedEffect(message) {
        if (message != null) {
            withFrameNanos { }
            feedback.bringIntoView()
        }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), vm::finishExport)
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::previewBackup)
    LaunchedEffect(prepared) {
        if (prepared != null && vm.claimExportPicker()) {
            try { create.launch("seen-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))}.json") }
            catch (_: Exception) { vm.finishExport(null) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
        Text("备份是明文 JSON，包含全部电影、观看记录、感受和海报。请保存到可信位置；不包含 TMDB 凭据和未保存草稿。", color = SeenSecondary)
        OutlinedButton(onClick = vm::prepareExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("导出全部数据") }
        if (preview == null) {
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                enabled = !busy && ready && draft == null, modifier = Modifier.fillMaxWidth()) { Text("选择备份恢复") }
            if (draft != null) Text("请先保存或放弃未完成的编辑，再恢复备份。")
        }
        preview?.let { value ->
            HorizontalDivider()
            Text("确认覆盖恢复", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("来源版本：${value.appVersion}\n导出时间：${value.exportedAt}")
            Text("备份：${value.movieCount} 部电影、${value.viewingCount} 次观看、${value.posterCount} 张海报\n本机：${value.currentMovies} 部电影、${value.currentViewings} 次观看")
            Text("电影数包含零观看记录的电影，可能与主页数量不同。")
            if (value.hasFutureDates) Text("备份含未来观看日期，请确认设备时钟。")
            Text("将替换本机全部电影与观看记录，不能合并。TMDB 凭据与偏好保留。", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = vm::prepareExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("先导出现有数据") }
            Button(onClick = vm::restoreBackup, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("覆盖恢复") }
            TextButton(onClick = { vm.cancelRestore() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("取消恢复") }
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        message?.let {
            Text(it, Modifier.bringIntoViewRequester(feedback).semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
