package com.seen.film.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun SeenBottomBar(route: String, enabled: Boolean, onLibrary: () -> Unit, onStatistics: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 40.dp)) {
                listOf(Triple("影库", "library", onLibrary), Triple("统计", "statistics", onStatistics)).forEach { (label, target, action) ->
                    val active = route == target
                    val tint = if (active) MaterialTheme.colorScheme.onSurface else SeenSecondary
                    Column(Modifier.weight(1f).testTag("tab-$label")
                        .selectable(active, enabled = enabled, role = Role.Tab, onClick = action)
                        .heightIn(min = 66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(18.dp).height(2.dp).background(if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                        Spacer(Modifier.height(12.dp))
                        Icon(if (target == "library") SeenIcons.Film else SeenIcons.Chart, null, Modifier.size(22.dp), tint = tint)
                        Spacer(Modifier.height(6.dp))
                        Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(9.dp))
                    }
                }
            }
        }
    }
}
