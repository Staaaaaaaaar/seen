@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
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
            Box(Modifier.selectable(active, role = Role.Tab, onClick = { onChange(query.copy(filter = filter)) })
                .widthIn(min = 48.dp).heightIn(min = 48.dp).padding(horizontal = if (compact) 4.dp else 8.dp), contentAlignment = Alignment.Center) {
                Text(filter.label, color = if (active) MaterialTheme.colorScheme.onSurface else SeenSecondary, style = MaterialTheme.typography.labelLarge)
                if (active) HorizontalDivider(Modifier.align(Alignment.BottomCenter).padding(bottom=4.dp).width(20.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.height(48.dp),contentAlignment=Alignment.Center){
            if(compact)IconButton(onClick=onSort){Icon(if(query.direction==SortDirection.ASC)Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,"排序电影",Modifier.size(20.dp),tint=SeenSecondary)}
            else TextButton(onClick=onSort,contentPadding=PaddingValues(horizontal=8.dp)){
                Text(query.sort.label,style=MaterialTheme.typography.labelLarge,color=SeenSecondary)
                Spacer(Modifier.width(6.dp));Icon(if(query.direction==SortDirection.ASC)Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,"排序电影",Modifier.size(18.dp),tint=SeenSecondary)
            }
        }
    }
}

@Composable
fun LibrarySortSheet(query: LibraryQuery, onChange: (LibraryQuery) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("排序", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            listOf(LibrarySort.TITLE,LibrarySort.YEAR,LibrarySort.RATING,LibrarySort.VIEWED).forEach { sort ->
                Row(Modifier.fillMaxWidth().selectable(query.sort == sort,role=Role.RadioButton,onClick={
                    onChange(query.copy(sort=sort,direction=if(sort==LibrarySort.TITLE)SortDirection.ASC else SortDirection.DESC));onDismiss()
                }).heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(sort.label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    val direction=if(query.sort==sort)query.direction else if(sort==LibrarySort.TITLE)SortDirection.ASC else SortDirection.DESC
                    IconButton(onClick={onChange(query.copy(sort=sort,direction=if(direction==SortDirection.ASC)SortDirection.DESC else SortDirection.ASC))}){
                        Icon(if(direction==SortDirection.ASC)Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,"切换${sort.label}方向",Modifier.size(20.dp),tint=if(query.sort==sort)MaterialTheme.colorScheme.primary else SeenSecondary)
                    }
                }
            }

        }
    }
}
