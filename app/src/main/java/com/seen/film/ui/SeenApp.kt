@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
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
fun SeenApp(vm:SeenViewModel=viewModel())=SeenTheme {
    var stack by rememberSaveable{mutableStateOf(listOf("library"))}
    val route=stack.last()
    val snapshot by vm.librarySnapshot.collectAsStateWithLifecycle()
    val all=snapshot?.movies
    val draft by vm.draft.collectAsStateWithLifecycle()
    val recordDraft by vm.recordDraft.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val metadataState by vm.metadataRefreshState.collectAsStateWithLifecycle()
    val metadataPreparing = metadataState is com.seen.film.data.MetadataRefreshState.Preparing
    val error by vm.error.collectAsStateWithLifecycle()
    val preview by vm.restorePreview.collectAsStateWithLifecycle()
    val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
    val pageStates=rememberSaveableStateHolder()
    val snackbar=remember{SnackbarHostState()}
    var confirmExit by remember{mutableStateOf(false)}
    var confirmNew by rememberSaveable{mutableStateOf(false)}
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle,vm){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)vm.persistDraft()};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
    fun push(value:String){if(stack.last()!=value)stack=stack+value}
    fun pop(){if(stack.size>1)stack=stack.dropLast(1)}
    fun back(){
        if(busy)return
        if(preview!=null){vm.cancelRestore{pop()};return}
        if(route.startsWith("record:")&&recordDraft!=null){if(vm.isRecordDirty)confirmExit=true else vm.discardRecord{pop()}}
        else if(route.startsWith("editor:")&&draft!=null){if(vm.isDirty)confirmExit=true else vm.discard{pop()}}
        else if(route=="statistics")stack=listOf("library") else pop()
    }
    BackHandler(stack.size>1||route=="statistics"){back()}
    LaunchedEffect(error){error?.let{snackbar.showSnackbar(it,withDismissAction=true);vm.clearError()}}
    LaunchedEffect(draftReady,route,draft?.key,recordDraft?.key,busy){if(draftReady&&route.startsWith("editor:")&&draft==null&&!busy)pop();if(draftReady&&route.startsWith("record:")&&recordDraft==null&&!busy)pop()}
    fun add(){if(metadataPreparing||busy||!draftReady||preview!=null)return;if(draft!=null||recordDraft!=null)confirmNew=true else push("choose")}
    Scaffold(contentWindowInsets=if(route.startsWith("movie:"))WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(route in listOf("library","statistics"))SeenBottomBar(route,!busy&&preview==null,{stack=listOf("library")},{stack=listOf("statistics")})
    }){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)){
            when {
                route=="library"->pageStates.SaveableStateProvider("library"){
                    if(loadFailed)PageMessage("无法读取影库","请重新打开应用，原有数据不会被清空。")
                    else LibraryScreen(all,draft,draftReady&&!busy&&preview==null,vm.app.posters::file,::add,{push("movie:$it")},
                        {if(recordDraft!=null)push("record:${recordDraft!!.key}") else draft?.let{push("editor:${it.key}")}},onSettings={push("settings")},initialQuery=vm.app.libraryPreferences.read(),
                        onQueryChanged=vm::saveLibraryQuery,languageCode=snapshot?.metadata?.activeLanguage?:"zh-CN",hasPendingRecord=recordDraft!=null,canAdd=draftReady&&!busy&&!metadataPreparing&&preview==null,preparationMessage=if(metadataPreparing)"资料准备中，暂缓增删影片；可在设置中取消。" else null)
                }
                route=="statistics"->pageStates.SaveableStateProvider("statistics"){
                    StatisticsScreen(all,onSettings={push("settings")})
                }
                route=="choose"->ChooseScreen(vm,all,!busy&&draftReady,{back()},{movieId->
                    if(movieId!=null)push("movie:$movieId") else vm.openMovieEditor{push("editor:$it")}
                },onChooseTmdb={key->
                    if(key.startsWith("movie:"))push(key) else push("editor:$key")
                })
                route.startsWith("movie:")->MovieScreen(route.removePrefix("movie:").substringBefore("/"),route.substringAfter("/","").ifBlank{null},vm,all,{back()},{movieId,kind,recordId->vm.openRecord(movieId,kind,recordId){push("record:$it")}})
                route.startsWith("record:")->recordDraft?.let{value->RecordEditorScreen(vm,value,{back()}){movieId,key->stack=listOf("library","movie:$movieId/$key")}}
                route.startsWith("editor:")->draft?.let{value->
                    EditorScreen(vm,value,{back()}){movieId,viewingId->stack=listOf("library",if(value.movieOnly)"movie:$movieId" else "movie:$movieId/$viewingId")}
                }
                route=="settings"->pageStates.SaveableStateProvider("settings"){SettingsScreen(vm,{push(it)},onBack={back()})}
                route.startsWith("settings:")->SettingsToolScreen(route.substringAfter(":"),vm){back()}
            }
        }
    }
    if(confirmNew)AlertDialog(onDismissRequest={confirmNew=false},title={Text("有未完成的编辑")},text={Text("先继续完成当前内容，或放弃后添加其他影片。")},
        confirmButton={TextButton(onClick={confirmNew=false;if(recordDraft!=null)push("record:${recordDraft!!.key}") else draft?.let{push("editor:${it.key}")}}){Text("继续编辑")}},
        dismissButton={Column{TextButton(onClick={if(recordDraft!=null)vm.discardRecord{confirmNew=false;push("choose")} else vm.discard{confirmNew=false;push("choose")}}){Text("放弃后新建")};TextButton(onClick={confirmNew=false}){Text("取消")}}})
    if(confirmExit)AlertDialog(onDismissRequest={confirmExit=false},title={Text("放弃未保存的修改？")},text={Text("退出会丢弃当前编辑的内容。")},
        confirmButton={TextButton(onClick={if(recordDraft!=null&&route.startsWith("record:"))vm.discardRecord{confirmExit=false;pop()} else vm.discard{confirmExit=false;pop()}},enabled=!busy){Text("放弃修改")}},dismissButton={TextButton(onClick={confirmExit=false}){Text("继续编辑")}})
}
