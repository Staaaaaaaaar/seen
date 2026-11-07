@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seen.film.data.Movie
import com.seen.film.data.MovieHistory
import com.seen.film.domain.*

@Composable
fun MovieScreen(id:String,focusViewingId:String?,vm:SeenViewModel,all:List<MovieHistory>?,onBack:()->Unit,onRecord:(String,RecordKind,String?)->Unit){
    val history=all?.firstOrNull{it.movie.id==id}
    val busy by vm.busy.collectAsState()
    val metadataState by vm.metadataRefreshState.collectAsState()
    val metadataPreparing=metadataState is com.seen.film.data.MetadataRefreshState.Preparing
    var menu by remember{mutableStateOf(false)}
    var deleteMovie by rememberSaveable{mutableStateOf(false)}
    var recordChoice by rememberSaveable{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<TimelineEntry?>(null)}
    var expandedBody by rememberSaveable(id){mutableStateOf(focusViewingId)}
    val state=rememberLazyListState()
    val context=LocalContext.current
    val density=LocalDensity.current
    val topChromePx=WindowInsets.statusBars.getTop(density)+with(density){66.dp.roundToPx()}
    val scrolled by remember{derivedStateOf{state.firstVisibleItemIndex>0}}
    val chromeColor=MaterialTheme.colorScheme.background
    val configuration=LocalConfiguration.current
    val wide=configuration.screenWidthDp>configuration.screenHeightDp
    SideEffect{
        (context as? Activity)?.window?.let{WindowCompat.getInsetsController(it,it.decorView).isAppearanceLightStatusBars=(scrolled||wide)&&chromeColor.luminance()>.5f}
    }
    DisposableEffect(context){
        val window=(context as? Activity)?.window
        val controller=window?.let{WindowCompat.getInsetsController(it,it.decorView)}
        val old=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose{if(old!=null)controller?.isAppearanceLightStatusBars=old}
    }
    when {
        all==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        history==null->PageMessage("电影已不存在","返回影库查看其他电影。","返回",onBack)
        else->{
            val movie=history.movie
            val entries=remember(history){movieTimeline(history)}
            LaunchedEffect(focusViewingId,entries){
                val index=entries.indexOfFirst{it.key==focusViewingId||it.id==focusViewingId}
                if(index>=0){expandedBody=entries[index].key;state.scrollToItem(index+3,-topChromePx)}
            }
            Box(Modifier.fillMaxSize()){
                LazyColumn(state=state,modifier=Modifier.fillMaxSize().navigationBarsPadding().testTag("movie-detail-list"),contentPadding=PaddingValues(bottom=32.dp)){
                    item{MovieHero(movie,vm)}
                    item{
                        Column(Modifier.padding(horizontal=24.dp).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                            Text(history.genres.sortedBy{it.position}.joinToString(" / "){it.name},style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                            RatingControl(movie.personalRatingHalfStars,!busy){vm.setMovieRating(id,it)}
                            movie.overview?.let{overview->
                                var expanded by rememberSaveable(id){mutableStateOf(false)}
                                ExpandableMovieText(overview,4,expanded,{expanded=!expanded},"简介",MaterialTheme.typography.bodyMedium.copy(fontFamily=SeenSerif,lineHeight=27.sp))
                            }
                            CreditsSection(history.credits,vm.app.portraits,movie.directorNames)
                        }
                    }
                    item{
                        Column(Modifier.padding(horizontal=24.dp).padding(top=26.dp)){
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))
                            Row(Modifier.fillMaxWidth().padding(top=9.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically){
                                Icon(SeenIcons.Journal,"个人记录",Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick={recordChoice=true},enabled=!busy,modifier=Modifier.testTag("record-add")){Icon(Icons.Outlined.Edit,"新增记录或留言",Modifier.size(21.dp))}
                            }
                        }
                    }
                    itemsIndexed(entries,key={_,entry->entry.key}){_,entry->
                        var entryMenu by remember{mutableStateOf(false)}
                        Column(Modifier.padding(horizontal=24.dp).padding(top=14.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Text(entry.date+(entry.time?.let{"  $it"}?:""),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                                if(entry.kind==RecordKind.VIEWING)Icon(SeenIcons.Film,"观看记录",Modifier.size(15.dp),tint=MaterialTheme.colorScheme.primary)
                                Box{
                                    IconButton(onClick={entryMenu=true},enabled=!busy){Icon(Icons.Outlined.MoreHoriz,"${entry.date} ${if(entry.kind==RecordKind.VIEWING)"观看记录" else "留言"}操作",Modifier.size(19.dp))}
                                    DropdownMenu(entryMenu,{entryMenu=false}){
                                        DropdownMenuItem(text={Text("编辑")},onClick={entryMenu=false;onRecord(id,entry.kind,entry.id)})
                                        DropdownMenuItem(text={Text("删除",color=MaterialTheme.colorScheme.error)},onClick={entryMenu=false;deleting=entry})
                                    }
                                }
                            }
                            entry.body?.let{body->
                                ExpandableMovieText(body,5,expandedBody==entry.key,{expandedBody=if(expandedBody==entry.key)null else entry.key},"正文",MaterialTheme.typography.bodyLarge.copy(fontFamily=SeenSerif,fontSize=15.sp,lineHeight=30.sp))
                            }
                            entry.location?.let{Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Outlined.LocationOn,null,Modifier.size(13.dp),tint=SeenSecondary);Text(it,style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                            }}
                            HorizontalDivider(Modifier.padding(top=10.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                        }
                    }
                    if(entries.isEmpty())item{EmptyNotice("还没有记录","",Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=32.dp))}

                }
                if(scrolled)Box(Modifier.fillMaxWidth().height(with(density){topChromePx.toDp()}).background(chromeColor))
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal=14.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween){
                    IconButton(onBack,modifier=Modifier.background(Color(0x99302820),CircleShape)){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回",tint=Color.White)}
                    Box{
                        IconButton(onClick={menu=true},enabled=!busy,modifier=Modifier.background(Color(0x99302820),CircleShape)){Icon(Icons.Outlined.MoreHoriz,"电影操作",tint=Color.White)}
                        DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("删除电影",color=MaterialTheme.colorScheme.error)},enabled=!metadataPreparing,onClick={menu=false;deleteMovie=true})}
                    }
                }
            }
        }
    }
    if(recordChoice&&history!=null)ModalBottomSheet(onDismissRequest={recordChoice=false},containerColor=MaterialTheme.colorScheme.surfaceContainerHigh){
        Column(Modifier.padding(horizontal=24.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            listOf(RecordKind.VIEWING to "这次观看",RecordKind.NOTE to "一段想法").forEach{(kind,label)->
                TextButton(onClick={recordChoice=false;onRecord(id,kind,null)},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp),contentPadding=PaddingValues(8.dp)){
                    Icon(if(kind==RecordKind.VIEWING)SeenIcons.Film else Icons.Outlined.Edit,null,Modifier.size(22.dp));Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.titleMedium);Text(if(kind==RecordKind.VIEWING)"计入观看次数，可留下时间、地点与感受" else "留下想法，不增加观看次数",style=MaterialTheme.typography.bodySmall,color=SeenSecondary)}
                }
            }
        }
    }
    if(deleteMovie&&history!=null)AlertDialog(onDismissRequest={deleteMovie=false},title={Text("删除这部电影？")},
        text={Text("将删除《${history.movie.title}》、个人评分、${history.viewings.size} 次观看与 ${history.notes.size} 条留言。")},
        confirmButton={TextButton(onClick={vm.deleteMovie(id){deleteMovie=false;onBack()}},enabled=!busy){Text("删除电影",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={deleteMovie=false}){Text("取消")}})
    deleting?.let{entry->AlertDialog(onDismissRequest={deleting=null},title={Text(if(entry.kind==RecordKind.VIEWING)"删除这次观看？" else "删除这条留言？")},
        text={Text("仅删除这条记录，电影、评分与其他内容会保留。")},confirmButton={TextButton(onClick={vm.deletePersonalRecord(entry.kind,entry.id);deleting=null},enabled=!busy){Text("删除记录",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})}
}

@Composable
private fun MovieHero(movie:Movie,vm:SeenViewModel){
    val separate=LocalDensity.current.fontScale>=1.5f||movie.title.codePointCount(0,movie.title.length)>18
    val background=MaterialTheme.colorScheme.background
    @Composable fun title(){Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(movie.title,fontFamily=SeenSerif,fontSize=34.sp,lineHeight=46.sp)
        movie.originalTitle?.takeIf{it!=movie.title}?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=SeenSecondary)}
        Text(listOfNotNull(movie.releaseYear?.toString(),movie.runtimeMinutes?.let{"$it 分钟"},movie.productionCountries).joinToString(" / "),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
    }}
    val configuration=LocalConfiguration.current
    if(configuration.screenWidthDp>configuration.screenHeightDp){
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top=66.dp,start=24.dp,end=24.dp,bottom=20.dp),verticalAlignment=Alignment.CenterVertically){
            Poster(vm.app.posters.file(movie.posterKey),Modifier.width(130.dp))
            Box(Modifier.weight(1f)){title()}
        }
        return
    }
    BoxWithConstraints{
        val heroHeight=maxWidth*1.5f
        Column{
            Box(Modifier.fillMaxWidth().height(heroHeight).clipToBounds()){
                Poster(vm.app.posters.file(movie.posterKey),Modifier.fillMaxSize(),cornerRadius=0.dp)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000),.45f to Color.Transparent,.72f to background.copy(alpha=.6f),1f to background)))
                if(!separate)Box(Modifier.align(Alignment.BottomStart)){title()}
            }
            if(separate)title()
        }
    }
}

@Composable
private fun ExpandableMovieText(text:String,lines:Int,expanded:Boolean,onToggle:()->Unit,label:String,style:androidx.compose.ui.text.TextStyle){
    var overflow by remember(text){mutableStateOf(false)}
    Column{
        SelectionContainer{
            Text(text,style=style,maxLines=if(expanded)Int.MAX_VALUE else lines,overflow=TextOverflow.Ellipsis,
                onTextLayout={if(!expanded)overflow=it.hasVisualOverflow})
        }
        if(overflow||expanded)IconButton(onClick=onToggle,modifier=Modifier.align(Alignment.End)){
            Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded)"收起$label" else "展开$label",Modifier.size(18.dp))
        }
    }
}
