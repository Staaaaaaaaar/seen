@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seen.film.data.MovieHistory
import com.seen.film.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LibraryScreen(
    all: List<MovieHistory>?, draft: ViewingDraft?, canEdit: Boolean,
    posterFile: (String?) -> File?, onAdd: () -> Unit, onMovie: (String) -> Unit, onResume: () -> Unit,
    onSettings: () -> Unit = {}, initialQuery: LibraryQuery = LibraryQuery(),
    onQueryChanged: (LibraryQuery) -> Unit = {}, languageCode: String = "zh-CN", hasPendingRecord: Boolean = false, canAdd: Boolean = canEdit, preparationMessage: String? = null,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(initialQuery.filter.name) }
    var sort by rememberSaveable { mutableStateOf((if(initialQuery.sort==LibrarySort.ADDED)LibrarySort.TITLE else initialQuery.sort).name) }
    var direction by rememberSaveable { mutableStateOf(initialQuery.direction.name) }
    var sortSheet by remember { mutableStateOf(false) }
    var orderingRevision by rememberSaveable { mutableStateOf(0) }
    val query = LibraryQuery(text,LibraryFilter.valueOf(filter),LibrarySort.valueOf(sort),SortDirection.valueOf(direction))
    fun change(value: LibraryQuery) { orderingRevision++;filter=value.filter.name;sort=value.sort.name;direction=value.direction.name;onQueryChanged(value.copy(text="")) }
    val prepared by produceState<Pair<LibraryQuery,List<MovieHistory>>?>(null,all,query,languageCode) {
        value = all?.let { query to withContext(Dispatchers.Default) { libraryMovies(it,query,languageCode) } }
    }
    val movies=prepared?.takeIf{it.first==query}?.second
    // Restart the list when sort changes. Old asynchronous results cannot retain an old scroll anchor.
    val orderingKey=sort+":"+direction+":"+orderingRevision
    BackHandler(search) { text="";search=false }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SeenTopBar(onSettings=onSettings,onSearch={search=!search;if(!search)text=""})
            if(search) OutlinedTextField(text,{text=it},Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=8.dp).testTag("library-search"),
                placeholder={Text("搜索片名、导演或类型")},singleLine=true,
                trailingIcon={IconButton(onClick={text="";search=false}){Icon(Icons.Outlined.Close,"关闭搜索")}})
            Spacer(Modifier.height(4.dp))
            LibraryControls(query,::change){sortSheet=true}
            Spacer(Modifier.height(4.dp))
            preparationMessage?.let{Text(it,Modifier.padding(horizontal=24.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)}
            if(draft!=null||hasPendingRecord)TextButton(onClick=onResume,enabled=canEdit,modifier=Modifier.padding(horizontal=14.dp)){
                Icon(Icons.Outlined.Edit,null,Modifier.size(16.dp));Spacer(Modifier.width(8.dp));Text("继续未完成的编辑",style=MaterialTheme.typography.bodySmall)
            }
            when {
                all==null || movies==null -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                all.isEmpty() -> PageMessage("还没有电影","把想看的或看过的电影加入影库。")
                movies!!.isEmpty() -> PageMessage("没有匹配的电影","试试其他筛选或关键词。")
                else -> key(orderingKey){LazyColumn(state=rememberLazyListState(),modifier=Modifier.fillMaxSize().testTag("library-list"),
                    contentPadding=PaddingValues(start=24.dp,end=24.dp,top=0.dp,bottom=110.dp)){
                    items(movies!!,key={it.movie.id}){history->
                        val density=LocalDensity.current
                        val cardHeight=maxOf(96.dp,with(density){28.sp.toDp()*2+19.sp.toDp()*2}+4.dp)
                        Row(Modifier.fillMaxWidth().testTag("library-row").clickable(onClickLabel="查看电影详情"){onMovie(history.movie.id)}
                            .semantics(mergeDescendants=true){}.padding(vertical=12.dp),
                            horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.Top){
                            Poster(posterFile(history.movie.posterKey),Modifier.width(cardHeight*2f/3f).height(cardHeight))
                            Row(Modifier.weight(1f).height(cardHeight),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.SpaceBetween){
                                    Text(history.movie.title,style=MaterialTheme.typography.titleMedium.copy(fontSize=19.sp,lineHeight=28.sp),maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                                        history.movie.releaseYear?.let{Text(it.toString(),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)}
                                        if(history.genres.isNotEmpty())Text(history.genres.sortedBy{it.position}.joinToString(" / "){it.name},style=MaterialTheme.typography.bodySmall,color=SeenSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    }
                                }
                                val rating=history.movie.personalRatingHalfStars
                                Box(Modifier.width(if(LocalDensity.current.fontScale>=1.4f)68.dp else 48.dp).heightIn(min=32.dp)
                                    .semantics{contentDescription=if(rating==null)"未评分" else "个人评分 ${ViewingRules.ratingLabel(rating)}，满分 5 星"},contentAlignment=Alignment.Center){
                                    Text(if(rating==null)"—" else ViewingRules.ratingLabel(rating),fontFamily=FontFamily.Serif,fontSize=22.sp,maxLines=1)
                                }
                            }
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                    }
                }}
            }
        }
        if(all!=null)FloatingActionButton(onClick={if(canAdd)onAdd()},modifier=Modifier.align(Alignment.BottomEnd).padding(22.dp).testTag("bottom-add").semantics{if(!canAdd)disabled()},
            shape=CircleShape,containerColor=if(canAdd)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,contentColor=if(canAdd)MaterialTheme.colorScheme.onPrimary else SeenSecondary){Icon(Icons.Outlined.Add,"添加影片")}
    }
    if(sortSheet)LibrarySortSheet(query,::change){sortSheet=false}
}
