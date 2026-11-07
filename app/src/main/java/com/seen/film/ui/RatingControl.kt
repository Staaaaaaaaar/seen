package com.seen.film.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StarHalf
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun RatingControl(value:Int?,enabled:Boolean,onChange:(Int?)->Unit){
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("personal-rating").semantics{
        contentDescription="个人评分"
        stateDescription=if(value==null)"未评分" else "${value/2f} 分"
        progressBarRangeInfo=ProgressBarRangeInfo((value?:0)/2f,0f..5f,9)
        if(enabled)customActions=listOf(CustomAccessibilityAction("清除评分"){onChange(null);true})
        if(enabled)setProgress{score->onChange((score*2).roundToInt().coerceIn(0,10));true}
    }){
        val stacked=maxWidth<340.dp||LocalDensity.current.fontScale>=1.4f
        @Composable fun stars(){Row{
            for(i in 1..5){
                val filled=(value?:0)>=i*2
                val half=(value?:0)==i*2-1
                Box(Modifier.size(48.dp).pointerInput(enabled,i,value){if(enabled)detectTapGestures(onLongPress={onChange(null)},onTap={offset->val next=if(offset.x<size.width/2f)i*2-1 else i*2;onChange(if(value==next)0 else next)})}
                    .semantics{
                        role=Role.Button;contentDescription="评为 $i 星"
                        if(enabled){onClick{onChange(if(value==i*2)0 else i*2);true};customActions=listOf(CustomAccessibilityAction("评为 ${i-.5} 星"){onChange(if(value==i*2-1)0 else i*2-1);true})}
                        else disabled()
                    },contentAlignment=Alignment.Center){
                    Icon(if(filled)Icons.Outlined.Star else if(half)Icons.AutoMirrored.Outlined.StarHalf else Icons.Outlined.StarBorder,null,
                        Modifier.size(23.dp),tint=if(filled||half)MaterialTheme.colorScheme.primary else SeenSecondary.copy(alpha=.6f))
                }
            }
        }}
        @Composable fun score(){Box(Modifier.widthIn(min=56.dp).heightIn(min=48.dp),contentAlignment=Alignment.Center){
            Text(value?.let{com.seen.film.domain.ViewingRules.ratingLabel(it)}?:"—",fontFamily=FontFamily.Serif,fontSize=24.sp)
        }}
        if(stacked)Column(verticalArrangement=Arrangement.spacedBy(4.dp)){stars();score()}
        else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){stars();score()}
    }
}
