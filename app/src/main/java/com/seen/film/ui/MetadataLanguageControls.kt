package com.seen.film.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.seen.film.data.MetadataRefreshState
import com.seen.film.domain.MetadataLanguage

@Composable
fun MetadataLanguageControls(vm:SeenViewModel){
    val snapshot by vm.librarySnapshot.collectAsState()
    val state by vm.metadataRefreshState.collectAsState()
    val ready by vm.metadataReady.collectAsState()
    val configured by vm.credentialConfigured.collectAsState()
    val busy by vm.busy.collectAsState()
    val draft by vm.draft.collectAsState()
    val pendingTmdbDraft=draft?.tmdbId!=null
    val active=MetadataLanguage.fromCode(snapshot?.metadata?.activeLanguage)
    val preparing=state is MetadataRefreshState.Preparing
    Text("当前：${active.displayName}",style=MaterialTheme.typography.titleMedium)
    Text("用于 TMDB 的片名、电影资料与默认海报。全部准备完成后一起切换，个人记录与评分保留。",color=SeenSecondary,style=MaterialTheme.typography.bodyMedium)
    MetadataLanguage.entries.forEach{language->
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).selectable(selected=language==active,enabled=ready&&!preparing&&!busy&&!pendingTmdbDraft&&(configured||snapshot?.movies?.none{it.movie.tmdbId!=null}==true),role=Role.RadioButton,onClick={vm.refreshMetadata(language)}),verticalAlignment=Alignment.CenterVertically){
            Text(language.displayName,Modifier.weight(1f));RadioButton(selected=language==active,onClick=null)
        }
    }
    when(val s=state){
        is MetadataRefreshState.Preparing->Column(Modifier.semantics{liveRegion=LiveRegionMode.Polite},verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("正在准备${s.target.displayName}资料 · ${s.done}/${s.total}")
            LinearProgressIndicator(progress={if(s.total==0)0f else s.done.toFloat()/s.total},modifier=Modifier.fillMaxWidth())
            Text("准备期间可继续阅读、留言和评分；增删影片与恢复备份暂缓。",style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
            TextButton(onClick=vm::cancelMetadataRefresh){Text("取消准备")}
        }
        is MetadataRefreshState.Failed->Text(s.message,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
        MetadataRefreshState.Interrupted->Text("上次资料准备已中断，当前资料保留，可重新更新。",color=SeenSecondary)
        is MetadataRefreshState.Completed->Text("资料已更新",color=SeenSecondary,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
        else->Unit
    }
    if(pendingTmdbDraft)Text("请先保存或放弃未完成的 TMDB 电影草稿，再更新资料。",color=SeenSecondary)
    if(!configured)Text("尚未配置 TMDB 凭据，可继续手动使用影库。",color=SeenSecondary)
    TextButton(onClick={vm.refreshMetadata(active)},enabled=configured&&ready&&!busy&&!preparing&&!pendingTmdbDraft){Text("更新电影资料")}
}
