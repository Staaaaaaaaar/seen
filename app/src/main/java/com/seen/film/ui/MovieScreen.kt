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
    var menu by remember{mutableStateOf(false)}
    var deleteMovie by rememberSaveable{mutableStateOf(false)}
    var recordChoice by rememberSaveable{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<TimelineEntry?>(null)}
    var expandedBody by rememberSaveable(id){mutableStateOf(focusViewingId)}
    val state=rememberLazyListState()
    val context=LocalContext.current
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
                if(index>=0){expandedBody=entries[index].key;state.scrollToItem(index+3)}
            }
            Box(Modifier.fillMaxSize()){
                LazyColumn(state=state,modifier=Modifier.fillMaxSize().navigationBarsPadding().testTag("movie-detail-list"),contentPadding=PaddingValues(bottom=32.dp)){
                    item{MovieHero(movie,vm)}
                    item{
                        Column(Modifier.padding(horizontal=24.dp).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Text(history.genres.sortedBy{it.position}.joinToString(" / "){it.name},Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically,
                                    modifier=Modifier.semantics(mergeDescendants=true){contentDescription="观看 ${history.viewings.size} 次"}){
                                    Icon(SeenIcons.Film,null,Modifier.size(14.dp),tint=SeenSecondary);Text(history.viewings.size.toString(),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                                }
                            }
                            RatingControl(movie.personalRatingHalfStars,!busy){vm.setMovieRating(id,it)}
                            movie.directorNames?.let{Row(horizontalArrangement=Arrangement.spacedBy(15.dp)){
                                Text("导演",style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                                Text(it,style=MaterialTheme.typography.bodyMedium)
                            }}
                            movie.overview?.let{overview->
                                var expanded by rememberSaveable(id){mutableStateOf(false)}
                                Column{Text(overview,style=MaterialTheme.typography.bodyMedium.copy(fontFamily=SeenSerif,lineHeight=27.sp),maxLines=if(expanded)Int.MAX_VALUE else 4,overflow=TextOverflow.Ellipsis)
                                    if(overview.length>90)IconButton(onClick={expanded=!expanded},modifier=Modifier.align(Alignment.End)){
                                        Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded)"收起简介" else "展开简介",Modifier.size(18.dp))
                                    }
                                }
                            }
                            CreditsSection(history.credits,vm.app.portraits)
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
                                SelectionContainer{Text(body,style=MaterialTheme.typography.bodyLarge.copy(fontFamily=SeenSerif,fontSize=15.sp,lineHeight=30.sp),maxLines=if(expandedBody==entry.key)Int.MAX_VALUE else 5,overflow=TextOverflow.Ellipsis)}
                                if(body.length>160)IconButton(onClick={expandedBody=if(expandedBody==entry.key)null else entry.key}){
                                    Icon(if(expandedBody==entry.key)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expandedBody==entry.key)"收起正文" else "展开正文",Modifier.size(18.dp))
                                }
                            }
                            entry.location?.let{Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Outlined.LocationOn,null,Modifier.size(13.dp),tint=SeenSecondary);Text(it,style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                            }}
                            HorizontalDivider(Modifier.padding(top=10.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                        }
                    }
                    if(entries.isEmpty())item{Text("还没有记录",Modifier.padding(horizontal=24.dp,vertical=18.dp),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)}
                    if(movie.tmdbId!=null)item{
                        val uri=LocalUriHandler.current
                        TextButton(onClick={uri.openUri("https://www.themoviedb.org/movie/${movie.tmdbId}")},modifier=Modifier.fillMaxWidth().padding(top=18.dp)){
                            Text("资料与海报来自 TMDB",style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                        }
                    }
                }
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal=14.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween){
                    IconButton(onBack,modifier=Modifier.background(Color(0x99101C2A),CircleShape)){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回",tint=Color.White)}
                    Box{
                        IconButton(onClick={menu=true},enabled=!busy,modifier=Modifier.background(Color(0x99101C2A),CircleShape)){Icon(Icons.Outlined.MoreHoriz,"电影操作",tint=Color.White)}
                        DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("删除电影",color=MaterialTheme.colorScheme.error)},onClick={menu=false;deleteMovie=true})}
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
    BoxWithConstraints{
        val heroHeight=maxWidth*1.48f
        Column{
            Box(Modifier.fillMaxWidth().height(heroHeight)){
                Poster(vm.app.posters.file(movie.posterKey),Modifier.fillMaxSize(),cornerRadius=0.dp)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000),.45f to Color.Transparent,.72f to background.copy(alpha=.6f),1f to background)))
                if(!separate)Box(Modifier.align(Alignment.BottomStart)){title()}
            }
            if(separate)title()
        }
    }
}
