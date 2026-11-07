@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seen.film.domain.ViewingDraft

@Composable
fun MovieEditorScreen(vm:SeenViewModel,draft:ViewingDraft,onBack:()->Unit,onSaved:(String,String)->Unit){
    val busy by vm.busy.collectAsState();val errors by vm.fieldErrors.collectAsState()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){it?.let(vm::selectPoster)}
    Scaffold(topBar={TopAppBar(title={Text("添加影片",style=MaterialTheme.typography.titleMedium)},navigationIcon={
        IconButton(onBack,enabled=!busy){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回")}
    },actions={TextButton(onClick={vm.save(onSaved)},enabled=!busy){Text(if(busy)"处理中…" else "保存")}})}){padding->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            if(draft.posterKey!=null)Poster(vm.app.posters.file(draft.posterKey),Modifier.width(96.dp))
            OutlinedTextField(draft.title,{v->vm.update{it.copy(title=v)}},Modifier.fillMaxWidth(),label={Text("电影名称 *")},enabled=!busy,singleLine=true,
                isError=errors.containsKey("title"),supportingText={errors["title"]?.let{Text(it)}})
            OutlinedTextField(draft.originalTitle,{v->vm.update{it.copy(originalTitle=v)}},Modifier.fillMaxWidth(),label={Text("原名（可选）")},enabled=!busy,singleLine=true,
                isError=errors.containsKey("originalTitle"),supportingText={errors["originalTitle"]?.let{Text(it)}})
            OutlinedTextField(draft.year,{v->vm.update{it.copy(year=v)}},Modifier.fillMaxWidth(),label={Text("上映年份（可选）")},enabled=!busy,singleLine=true,
                isError=errors.containsKey("year"),supportingText={errors["year"]?.let{Text(it)}})
            if(draft.tmdbId==null)TextButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!busy){Text(if(draft.posterKey==null)"选择海报（可选）" else "更换海报")}
            Text("评分和观看记录可以稍后在电影详情中添加。",style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
        }
    }
}
