@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seen.film.data.MovieHistory
import com.seen.film.domain.*
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StatisticsScreen(all:List<MovieHistory>?,onSettings:()->Unit){
    val period=StatisticsPeriod.ALL
    var expanded by rememberSaveable{mutableStateOf(false)}
    val today=LocalDate.now()
    val statistics by produceState<LibraryStatistics?>(null,all,period,today){
        value=all?.let{withContext(Dispatchers.Default){libraryStatistics(it,period,today)}}
    }
    Column(Modifier.fillMaxSize()){
        TopAppBar(title={SeenWordmark()},actions={IconButton(onClick=onSettings){Icon(Icons.Outlined.Settings,"设置")}})
        LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"),contentPadding=PaddingValues(start=24.dp,end=24.dp,top=32.dp,bottom=40.dp),verticalArrangement=Arrangement.spacedBy(40.dp)){
            val s=statistics
            if(s==null)item{CircularProgressIndicator(Modifier.size(24.dp))} else {
                item{
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)){
                        CountColumn(s.movieCount,"部电影","statistics-movie-count",Modifier.weight(1f))
                        CountColumn(s.viewingCount,"次观看","statistics-viewing-count",Modifier.weight(1f))
                    }
                }
                item{
                    Column(verticalArrangement=Arrangement.spacedBy(18.dp)){
                        StatisticHeading("每月观看")
                        val months=if(s.months.isEmpty())(1..12).map{MonthCount(YearMonth.of(today.year,it),0,false)} else s.months
                        MonthChart(months,period)
                    }
                }
                item{
                    Column(verticalArrangement=Arrangement.spacedBy(18.dp)){
                        StatisticHeading("电影类型")
                        if(s.genres.isEmpty())EmptyNotice("无记录","",Modifier.fillMaxWidth().padding(vertical=24.dp))
                        val max=s.genres.firstOrNull()?.count?:1
                        (if(expanded)s.genres else s.genres.take(5)).forEach{genre->
                            Column(Modifier.semantics(mergeDescendants=true){contentDescription="${genre.name}，${genre.count} 部电影"},verticalArrangement=Arrangement.spacedBy(10.dp)){
                                Row(Modifier.fillMaxWidth()){
                                    Text(genre.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                    Text(genre.count.toString(),color=SeenSecondary,style=MaterialTheme.typography.bodyMedium)
                                }
                                Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)){
                                    Box(Modifier.fillMaxWidth(genre.count.toFloat()/max).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                                }
                            }
                        }
                        if(s.genres.size>5)TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)){
                            Text(if(expanded)"收起类型" else "展开全部类型")
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun CountColumn(count:Int,label:String,tag:String,modifier:Modifier){
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(count.toString(),Modifier.testTag(tag),fontFamily=SeenSerif,fontSize=44.sp,lineHeight=54.sp)
        Text(label,style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
    }
}
@Composable private fun StatisticHeading(text:String){
    Text(text,Modifier.semantics{heading()},fontFamily=SeenSerif,style=MaterialTheme.typography.titleMedium)
}
@Composable private fun MonthChart(months:List<MonthCount>,period:StatisticsPeriod){
    val max=(months.maxOfOrNull{it.count}?:0).coerceAtLeast(1)
    val bar=MaterialTheme.colorScheme.primary
    val baseline=MaterialTheme.colorScheme.outlineVariant
    LazyRow(Modifier.fillMaxWidth().testTag("monthly-chart"),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        items(months,key={it.month.toString()}){m->
            Column(Modifier.width(if(period==StatisticsPeriod.ALL)58.dp else 30.dp).semantics(mergeDescendants=true){
                contentDescription="${m.month.year}年${m.month.monthValue}月，${m.count}次观看"+(if(m.isFuture)"，未来月份" else "")
            },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(if(m.isFuture&&m.count==0)"—" else m.count.toString(),color=SeenSecondary,style=MaterialTheme.typography.labelSmall)
                Canvas(Modifier.width(18.dp).height(100.dp)){
                    drawLine(baseline,Offset(0f,size.height-1f),Offset(size.width,size.height-1f),1.dp.toPx())
                    if(m.count>0){val h=(size.height-2.dp.toPx())*m.count/max;drawRect(bar.copy(alpha=if(m.isFuture).35f else .85f),Offset(0f,size.height-h),Size(size.width,h))}
                }
                Text(if(period==StatisticsPeriod.ALL)"${m.month.year}.${m.month.monthValue.toString().padStart(2,'0')}" else m.month.monthValue.toString(),color=SeenSecondary,style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
