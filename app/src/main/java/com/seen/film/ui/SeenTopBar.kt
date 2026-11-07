@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Root pages share the same wordmark inset, toolbar height and settings target. */
@Composable
fun SeenTopBar(onSettings:()->Unit,onSearch:(()->Unit)?=null){
    TopAppBar(title={Box(Modifier.padding(start=8.dp).testTag("seen-wordmark")){SeenWordmark()}},actions={
        onSearch?.let{action->
            IconButton(onClick=action,modifier=Modifier.size(48.dp)){
                Icon(Icons.Outlined.Search,"搜索影库",Modifier.size(22.dp))
            }
        }
        IconButton(onClick=onSettings,modifier=Modifier.size(48.dp).testTag("root-settings")){
            Icon(Icons.Outlined.Settings,"设置",Modifier.size(21.dp))
        }
    })
}
