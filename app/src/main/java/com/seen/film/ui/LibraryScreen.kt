@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
    onQueryChanged: (LibraryQuery) -> Unit = {}, languageCode: String = "zh-CN",
) {
    var text by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(initialQuery.filter.name) }
    var sort by rememberSaveable { mutableStateOf(initialQuery.sort.name) }
    var direction by rememberSaveable { mutableStateOf(initialQuery.direction.name) }
    var sortSheet by remember { mutableStateOf(false) }
    val query = LibraryQuery(text,LibraryFilter.valueOf(filter),LibrarySort.valueOf(sort),SortDirection.valueOf(direction))
    fun change(value: LibraryQuery) { filter=value.filter.name;sort=value.sort.name;direction=value.direction.name;onQueryChanged(value.copy(text="")) }
    val listState=rememberLazyListState()
    val movies by produceState<List<MovieHistory>?>(null,all,query,languageCode) {
        value = all?.let { withContext(Dispatchers.Default) { libraryMovies(it,query,languageCode) } }
    }
    BackHandler(search) { text="";search=false }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title={Text("Seen",fontFamily=FontFamily.Serif,fontStyle=FontStyle.Italic,fontSize=29.sp)},actions={
                IconButton(onClick={search=!search;if(!search)text=""}){Icon(Icons.Outlined.Search,"搜索影库",Modifier.size(22.dp))}
                IconButton(onClick=onSettings){Icon(Icons.Outlined.Settings,"设置",Modifier.size(21.dp))}
            })
            if(search) OutlinedTextField(text,{text=it},Modifier.fillMaxWidth().padding(horizontal=22.dp).padding(bottom=8.dp).testTag("library-search"),
                placeholder={Text("搜索片名、导演或类型")},singleLine=true,
                trailingIcon={IconButton(onClick={text="";search=false}){Icon(Icons.Outlined.Close,"关闭搜索")}})
            LibraryControls(query,::change){sortSheet=true}
            if(draft!=null)TextButton(onClick=onResume,enabled=canEdit,modifier=Modifier.padding(horizontal=14.dp)){
                Icon(Icons.Outlined.Edit,null,Modifier.size(16.dp));Spacer(Modifier.width(8.dp));Text("继续未完成的编辑",style=MaterialTheme.typography.bodySmall)
            }
            when {
                all==null || movies==null -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                all.isEmpty() -> PageMessage("还没有电影","把想看的或看过的电影加入影库。")
                movies!!.isEmpty() -> PageMessage("没有匹配的电影","试试其他筛选或关键词。")
                else -> LazyColumn(state=listState,modifier=Modifier.fillMaxSize().testTag("library-list"),
                    contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=110.dp)){
                    items(movies!!,key={it.movie.id}){history->
                        Row(Modifier.fillMaxWidth().testTag("library-row").clickable(onClickLabel="查看电影详情"){onMovie(history.movie.id)}
                            .semantics(mergeDescendants=true){}.padding(vertical=14.dp),
                            horizontalArrangement=Arrangement.spacedBy(17.dp),verticalAlignment=Alignment.CenterVertically){
                            Poster(posterFile(history.movie.posterKey),Modifier.width(56.dp))
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text(history.movie.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                    history.movie.releaseYear?.let{Text(it.toString(),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)}
                                    if(history.viewings.isNotEmpty())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp),
                                        modifier=Modifier.semantics(mergeDescendants=true){contentDescription="观看 ${history.viewings.size} 次"}){
                                        Icon(SeenIcons.Film,null,Modifier.size(12.dp),tint=SeenSecondary)
                                        Text(history.viewings.size.toString(),style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                                    }
                                }
                            }
                            val rating=history.movie.personalRatingHalfStars
                            if(rating==null)Text("—",style=MaterialTheme.typography.bodyMedium,color=SeenSecondary,modifier=Modifier.semantics{contentDescription="未评分"})
                            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp),
                                modifier=Modifier.semantics(mergeDescendants=true){contentDescription="个人评分 ${ViewingRules.ratingLabel(rating)}，满分 5 星"}){
                                Icon(Icons.Outlined.Star,null,Modifier.size(10.dp),tint=SeenSecondary)
                                Text(ViewingRules.ratingLabel(rating),fontFamily=FontFamily.Serif,fontSize=22.sp)
                            }
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                    }
                }
            }
        }
        if(all!=null)FloatingActionButton(onClick={if(canEdit)onAdd()},modifier=Modifier.align(Alignment.BottomEnd).padding(22.dp).testTag("bottom-add"),
            containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary){Icon(Icons.Outlined.Add,"添加影片")}
    }
    if(sortSheet)LibrarySortSheet(query,::change){sortSheet=false}
}
