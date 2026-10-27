@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.activity.compose.BackHandler
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.automirrored.outlined.StarHalf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seen.film.domain.ViewingDraft
import com.seen.film.domain.ViewingRules
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun EditorScreen(vm: SeenViewModel, draft: ViewingDraft, onBack: () -> Unit, onSaved: (String, String) -> Unit) {
    val busy by vm.busy.collectAsState()
    val errors by vm.fieldErrors.collectAsState()
    var more by rememberSaveable(draft.key) { mutableStateOf(draft.time.isNotBlank() || draft.location.isNotBlank()) }
    var thoughts by rememberSaveable(draft.key) { mutableStateOf(draft.notes.isNotEmpty()) }
    val manual = draft.isNewMovie && draft.tmdbId == null
    var movieStep by rememberSaveable(draft.key) { mutableStateOf(manual || draft.forWatchlist && draft.isNewMovie) }
    var movieErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    fun back() {
        if (!draft.forWatchlist && manual && !movieStep) movieStep = true
        else if (!draft.forWatchlist && !manual && movieStep) movieStep = false
        else onBack()
    }
    BackHandler(!draft.forWatchlist && ((manual && !movieStep) || (!manual && movieStep)) && !busy) { back() }
    LaunchedEffect(errors) {
        val metadataErrors = errors.filterKeys { it in setOf("title", "originalTitle", "year") }
        if (draft.isNewMovie && metadataErrors.isNotEmpty()) {
            movieErrors = metadataErrors
            movieStep = true
        }
    }
    val context = LocalContext.current
    val pickPoster = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::selectPoster) }
    fun openDate() {
        if (busy) return
        val day = runCatching { LocalDate.parse(draft.date) }.getOrDefault(LocalDate.now())
        DatePickerDialog(context, { _, year, month, date ->
            vm.update { it.copy(date = LocalDate.of(year, month + 1, date).toString()) }
        }, day.year, day.monthValue - 1, day.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis(); show()
        }
    }
    fun openTime() {
        if (busy) return
        val time = runCatching { LocalTime.parse(draft.time) }.getOrDefault(LocalTime.of(20, 0))
        TimePickerDialog(context, { _, hour, minute ->
            vm.update { it.copy(time = "%02d:%02d".format(java.util.Locale.ROOT, hour, minute)) }
        }, time.hour, time.minute, true).show()
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (movieStep) "电影资料" else if (draft.forWatchlist) "加入片单" else if (draft.isEditing) "编辑观看" else "添加观看", style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(::back, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        }, actions = {
            TextButton(onClick = {
                if (movieStep && !draft.forWatchlist) {
                    movieErrors = ViewingRules.validate(draft).filterKeys { it in setOf("title", "originalTitle", "year") }
                    if (movieErrors.isEmpty()) movieStep = false
                } else vm.save(onSaved)
            }, enabled = !busy) { Text(if (busy) "处理中…" else if (movieStep && !draft.forWatchlist) "下一步" else "保存") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (movieStep) {
                OutlinedTextField(draft.title, { value -> vm.update { it.copy(title = value) } }, Modifier.fillMaxWidth(), label = { Text("电影名称 *") }, enabled = !busy, singleLine = true,
                    isError = movieErrors.containsKey("title"), supportingText = { movieErrors["title"]?.let { Text(it) } })
                OutlinedTextField(draft.originalTitle, { value -> vm.update { it.copy(originalTitle = value) } }, Modifier.fillMaxWidth(), label = { Text("原名（可选）") }, enabled = !busy, singleLine = true,
                    isError = movieErrors.containsKey("originalTitle"), supportingText = { movieErrors["originalTitle"]?.let { Text(it) } })
                OutlinedTextField(draft.year, { value -> vm.update { it.copy(year = value) } }, Modifier.fillMaxWidth(), label = { Text("上映年份（可选）") }, enabled = !busy, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = movieErrors.containsKey("year"), supportingText = { movieErrors["year"]?.let { Text(it) } })
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (draft.posterKey != null) Poster(vm.app.posters.file(draft.posterKey), Modifier.width(72.dp))
                    OutlinedButton(enabled = !busy, onClick = { pickPoster.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (draft.posterKey == null) "选择海报（可选）" else "更换海报")
                    }
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Poster(vm.app.posters.file(draft.posterKey), Modifier.width(56.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(draft.title, style = MaterialTheme.typography.titleLarge)
                    Text(listOf(draft.year, draft.originalTitle).filter { it.isNotBlank() }.joinToString(" · "), color = SeenSecondary)
                }
            }
            if (!movieStep && !draft.forWatchlist) {
                if (draft.isNewMovie) TextButton(onClick = { movieStep = true }, enabled = !busy) { Text("修改电影资料") }
                if (draft.isNewMovie && draft.tmdbId != null) {
                    OutlinedButton(enabled = !busy, onClick = { pickPoster.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (draft.posterKey == null) "选择海报（可选）" else "更换海报")
                    }
                }
                HorizontalDivider()
                Surface(onClick = { openDate() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                    FlowRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("观看日期", style = MaterialTheme.typography.titleMedium)
                        Text(draft.date, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                errors["date"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("个人评分 · ${ViewingRules.ratingLabel(draft.rating)}", style = MaterialTheme.typography.titleMedium)
                BoxWithConstraints {
                    val starSize = ((maxWidth.value - 96f) / 5f).coerceIn(12f, 30f).dp
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(enabled = !busy && draft.rating != null && draft.rating > 1, onClick = { vm.update { it.copy(rating = ((it.rating ?: 2) - 1).coerceAtLeast(1)) } }) {
                            Icon(Icons.Outlined.Remove, "减少半星")
                        }
                        Row(Modifier.semantics { contentDescription = "${ViewingRules.ratingLabel(draft.rating)}，共 5 星" }) {
                            for (i in 1..5) {
                                val rating = draft.rating ?: 0
                                val icon = if (rating >= i * 2) Icons.Outlined.Star else if (rating == i * 2 - 1) Icons.AutoMirrored.Outlined.StarHalf else Icons.Outlined.StarBorder
                                Icon(icon, null, Modifier.size(starSize))
                            }
                        }
                        IconButton(enabled = !busy && (draft.rating == null || draft.rating < 10), onClick = { vm.update { it.copy(rating = ((it.rating ?: 0) + 1).coerceAtMost(10)) } }) {
                            Icon(Icons.Outlined.Add, "增加半星")
                        }
                    }
                }
                if (draft.rating != null) TextButton(enabled = !busy, onClick = { vm.update { it.copy(rating = null) } }) { Text("清除评分") }
                HorizontalDivider()
                TextButton(onClick = { more = !more }, enabled = !busy) { Text(if (more) "收起时间与地点" else "更多信息 · 时间、地点（可选）") }
                if (more) {
                    FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openTime() }, enabled = !busy) { Text(draft.time.ifBlank { "选择时间" }) }
                        if (draft.time.isNotBlank()) TextButton(enabled = !busy, onClick = { vm.update { it.copy(time = "") } }) { Text("清除时间") }
                    }
                    errors["time"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(draft.location, { value -> vm.update { it.copy(location = value) } }, Modifier.fillMaxWidth(), label = { Text("地点（可选）") }, enabled = !busy, singleLine = true,
                        isError = errors.containsKey("location"), supportingText = { errors["location"]?.let { Text(it) } })
                }
                HorizontalDivider()
                TextButton(onClick = { thoughts = !thoughts }, enabled = !busy) { Text(if (thoughts) "收起感受" else "添加感受（可选）") }
                if (thoughts) OutlinedTextField(draft.notes, { value -> vm.update { it.copy(notes = value) } }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    label = { Text("想法与感受") }, enabled = !busy, minLines = 5,
                    isError = errors.containsKey("notes"), supportingText = { errors["notes"]?.let { Text(it) } })
                errors["rating"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                // Also keep validation visible when an optional section is collapsed.
                if (!more) listOfNotNull(errors["time"], errors["location"]).forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!thoughts) errors["notes"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
