package com.seen.film.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.seen.film.data.MovieCredit
import com.seen.film.data.PortraitStore
import java.io.File

@Composable
fun CreditsSection(credits:List<MovieCredit>,portraits:PortraitStore?=null,directorNames:String?=null){
    if(credits.isEmpty()&&directorNames.isNullOrBlank())return
    var selected by remember{mutableStateOf<MovieCredit?>(null)}
    val directors=credits.filter{!it.isCast&&it.job=="Director"}.sortedBy{it.position}
    val cast=credits.filter{it.isCast}.sortedBy{it.position}
    val crew=credits.filter{!it.isCast&&it.job in setOf("Writer","Screenplay","Producer","Original Music Composer","Director of Photography")}.sortedBy{it.position}
    val fallback=if(directors.isEmpty())directorNames?.split(" / ")?.mapIndexed{i,name->MovieCredit("","director-fallback-$i",0,name,false,job="Director",position=i)}.orEmpty() else emptyList()
    val all=directors+fallback+cast+crew
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("演职人员",style=MaterialTheme.typography.titleSmall)
        LazyRow(modifier=Modifier.testTag("credits-list"),horizontalArrangement=Arrangement.spacedBy(16.dp)){
            items(all,key={it.creditKey}){credit->
                val file by produceState<File?>(null,credit.profilePath){value=credit.profilePath?.let{portraits?.file(it)}}
                Column(Modifier.width(72.dp).clickable{selected=credit},verticalArrangement=Arrangement.spacedBy(6.dp)){
                    if(file!=null)Poster(file,Modifier.fillMaxWidth(),cornerRadius=4.dp)
                    else Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.small){
                        Box(Modifier.fillMaxWidth().height(108.dp),contentAlignment=Alignment.Center){Text(credit.name.take(1),style=MaterialTheme.typography.titleLarge,color=SeenSecondary)}
                    }
                    Text(credit.name,Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(credit.character?.takeIf{it.isNotBlank()}?:credit.job?.let(::creditJobLabel)?:"演员",Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelMedium,color=SeenSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
    selected?.let{person->AlertDialog(onDismissRequest={selected=null},title={Text(person.name)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            person.character?.let{Text(it)};person.job?.let{Text(creditJobLabel(it))};person.department?.let{Text(it,color=SeenSecondary)}
        }
    },confirmButton={TextButton(onClick={selected=null}){Text("关闭")}})}
}
private fun creditJobLabel(job:String)=when(job){"Director"->"导演";"Writer","Screenplay"->"编剧";"Producer"->"制片";"Original Music Composer"->"配乐";"Director of Photography"->"摄影";else->job}
