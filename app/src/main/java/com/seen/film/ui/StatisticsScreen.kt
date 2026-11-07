@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StatisticsScreen(all:List<MovieHistory>?,onSettings:()->Unit){
    val period=StatisticsPeriod.ALL
    val today=LocalDate.now()
    val statistics by produceState<LibraryStatistics?>(null,all,period,today){
        value=all?.let{withContext(Dispatchers.Default){libraryStatistics(it,period,today)}}
    }
    Column(Modifier.fillMaxSize()){
        SeenTopBar(onSettings=onSettings)
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
                        StatisticHeading("近期观看")
                        val months=recentViewingMonths(s.months,today)
                        MonthChart(months)
                    }
                }
                item{
                    Column(verticalArrangement=Arrangement.spacedBy(18.dp)){
                        StatisticHeading("电影类型")
                        GenreDonut(genreSlices(s.genres))
                    }
                }
            }
        }
    }
}
@Composable private fun CountColumn(count:Int,label:String,tag:String,modifier:Modifier){
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(count.toString(),Modifier.testTag(tag),fontFamily=SeenSerif,fontSize=44.sp,lineHeight=54.sp)
        Text(label,style=MaterialTheme.typography.bodySmall,color=SeenSecondary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable private fun StatisticHeading(text:String){
    Text(text,Modifier.semantics{heading()},fontFamily=SeenSerif,style=MaterialTheme.typography.titleMedium)
}
@Composable private fun MonthChart(months:List<MonthCount>){
    val max=(months.maxOfOrNull{it.count}?:0).coerceAtLeast(1)
    val bar=MaterialTheme.colorScheme.primary
    val baseline=MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().testTag("monthly-chart"),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        months.forEach{m->
            Column(Modifier.weight(1f).semantics(mergeDescendants=true){
                contentDescription="${m.month.year}年${m.month.monthValue}月，${m.count}次观看"+(if(m.isFuture)"，未来月份" else "")
            },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(if(m.isFuture&&m.count==0)"—" else m.count.toString(),color=SeenSecondary,style=MaterialTheme.typography.labelSmall)
                Canvas(Modifier.width(18.dp).height(100.dp)){
                    drawLine(baseline,Offset(0f,size.height-1f),Offset(size.width,size.height-1f),1.dp.toPx())
                    if(m.count>0){val h=(size.height-2.dp.toPx())*m.count/max;drawRect(bar.copy(alpha=if(m.isFuture).35f else .85f),Offset(0f,size.height-h),Size(size.width,h))}
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally){
                    Text("${m.month.monthValue}月",color=SeenSecondary,style=MaterialTheme.typography.labelSmall,maxLines=1)
                }
            }
        }
    }
}
