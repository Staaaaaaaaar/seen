@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.seen.film.domain.*

@Composable
fun LibraryControls(query: LibraryQuery, onChange: (LibraryQuery) -> Unit, onSort: () -> Unit) {
    val compact = LocalDensity.current.fontScale >= 1.4f
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LibraryFilter.entries.forEach { filter ->
            val active = query.filter == filter
            Column(Modifier.selectable(active, role = Role.Tab, onClick = { onChange(query.copy(filter = filter)) })
                .heightIn(min = 48.dp).padding(end = if (compact) 8.dp else 22.dp), verticalArrangement = Arrangement.Center) {
                Text(filter.label, color = if (active) MaterialTheme.colorScheme.onSurface else SeenSecondary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                if (active) HorizontalDivider(Modifier.width(20.dp), color = MaterialTheme.colorScheme.primary)
                else Spacer(Modifier.height(1.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        val label = when (query.sort) {
            LibrarySort.ADDED -> if (query.direction == SortDirection.DESC) "最近添加" else "最早添加"
            LibrarySort.VIEWED, LibrarySort.RECENT -> if (query.direction == SortDirection.DESC) "最近观看" else "最早观看"
            else -> query.sort.label
        }
        if (compact) IconButton(onClick = onSort) { Icon(Icons.AutoMirrored.Outlined.Sort,"排序电影",Modifier.size(20.dp),tint=SeenSecondary) }
        else TextButton(onClick = onSort, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = SeenSecondary)
            Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.Sort,"排序电影",Modifier.size(18.dp),tint=SeenSecondary)
        }
    }
}

@Composable
fun LibrarySortSheet(query: LibraryQuery, onChange: (LibraryQuery) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("排序", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SortDirection.entries.forEach { direction ->
                    val text = when (query.sort) {
                        LibrarySort.TITLE -> if (direction == SortDirection.ASC) "名称升序" else "名称降序"
                        LibrarySort.RATING -> if (direction == SortDirection.ASC) "低到高" else "高到低"
                        else -> if (direction == SortDirection.ASC) "旧到新" else "新到旧"
                    }
                    FilterChip(selected = query.direction == direction, onClick = { onChange(query.copy(direction = direction)) },label = { Text(text) })
                }
            }
            Spacer(Modifier.height(10.dp))
            listOf(LibrarySort.ADDED,LibrarySort.TITLE,LibrarySort.YEAR,LibrarySort.RATING,LibrarySort.VIEWED).forEach { sort ->
                Row(Modifier.fillMaxWidth().selectable(query.sort == sort,role=Role.RadioButton,onClick={
                    onChange(query.copy(sort=sort,direction=if(sort==LibrarySort.TITLE)SortDirection.ASC else SortDirection.DESC));onDismiss()
                }).padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(sort.label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    if(query.sort==sort)Icon(Icons.Outlined.Check,null,Modifier.size(18.dp))
                }
            }
            Text("未评分或暂无观看记录的电影，在对应排序中排在最后。",style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
        }
    }
}
