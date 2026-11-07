package com.seen.film.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.seen.film.domain.GenreSlice

/** Warm muted inks keep the ring subordinate to the film journal's typography. */
@Composable
fun GenreDonut(slices:List<GenreSlice>){
    val colors=SeenGenreColors
    val track=MaterialTheme.colorScheme.outlineVariant
    val total=slices.sumOf{it.count}
    @Composable fun ring(modifier:Modifier){
        Box(modifier.aspectRatio(1f).testTag("genre-chart"),contentAlignment=Alignment.Center){
            Canvas(Modifier.fillMaxSize().semantics{
                contentDescription=if(total==0)"电影类型，无记录" else "电影类型分布："+slices.joinToString("，"){it.name}
            }){
                val stroke=size.minDimension*.17f
                val inset=stroke/2f
                val arcSize=Size(size.width-stroke,size.height-stroke)
                drawArc(track,0f,360f,false,Offset(inset,inset),arcSize,style=Stroke(stroke))
                var start=-90f
                slices.forEachIndexed{index,slice->
                    val sweep=360f*slice.count/total
                    drawArc(colors[index%colors.size],start,sweep,false,Offset(inset,inset),arcSize,style=Stroke(stroke))
                    start+=sweep
                }
            }
            if(total==0)Text("无记录",style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
        }
    }
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)){
        ring(Modifier.fillMaxWidth(.82f).widthIn(max=272.dp))
        if(slices.isNotEmpty()){
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)){
                slices.chunked(3).forEach{row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        row.forEach{slice->
                            val index=slices.indexOf(slice)
                            Box(Modifier.weight(1f),contentAlignment=Alignment.Center){
                                Row(Modifier.semantics(mergeDescendants=true){contentDescription=slice.name},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    Box(Modifier.size(7.dp).background(colors[index%colors.size],CircleShape))
                                    Text(slice.name,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                        repeat(3-row.size){Spacer(Modifier.weight(1f))}
                    }
                }
            }
        }
    }
}
