@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seen.film.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.seen.film.domain.*
import java.time.LocalDate

@Composable
fun RecordEditorScreen(vm:SeenViewModel,draft:RecordDraft,onBack:()->Unit,onSaved:(String,String)->Unit){
    val busy by vm.busy.collectAsState()
    val library by vm.library.collectAsState();val errors by vm.fieldErrors.collectAsState()
    val context=LocalContext.current
    val title=if(draft.kind==RecordKind.VIEWING)"这次观看" else "一段想法"
    fun pickDate(){val date=runCatching{LocalDate.parse(draft.date)}.getOrDefault(LocalDate.now())
        DatePickerDialog(context,{_,y,m,d->vm.updateRecord{it.copy(date=LocalDate.of(y,m+1,d).toString())}},date.year,date.monthValue-1,date.dayOfMonth)
            .apply{datePicker.maxDate=System.currentTimeMillis();show()}}
    Scaffold(topBar={TopAppBar(title={Text(if(draft.isEditing)"编辑$title" else title,style=MaterialTheme.typography.titleMedium)},navigationIcon={
        IconButton(onBack,enabled=!busy){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回")}
    },actions={TextButton(onClick={vm.saveRecord(onSaved)},enabled=!busy){Text(if(busy)"处理中…" else "保存")}})}){padding->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            library?.firstOrNull{it.movie.id==draft.movieId}?.movie?.title?.let{Text(it,style=MaterialTheme.typography.titleLarge)}
            BoxWithConstraints{
                @Composable fun date(modifier:Modifier){Column(modifier){
                    Text("日期",style=MaterialTheme.typography.bodySmall,color=SeenSecondary)
                    TextButton(onClick=::pickDate,enabled=!busy,modifier=Modifier.heightIn(min=56.dp).testTag("record-date")){Text(draft.date,style=MaterialTheme.typography.bodyLarge)}
                    errors["date"]?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                }}
                @Composable fun time(modifier:Modifier){OutlinedTextField(draft.time,{v->vm.updateRecord{it.copy(time=v)}},modifier.testTag("record-time"),label={Text("时间（可选）")},
                    singleLine=true,enabled=!busy,isError=errors.containsKey("time"),supportingText={errors["time"]?.let{Text(it)}})}
                if(maxWidth<320.dp||LocalDensity.current.fontScale>=1.4f)Column(verticalArrangement=Arrangement.spacedBy(12.dp)){date(Modifier.fillMaxWidth());time(Modifier.fillMaxWidth())}
                else Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){date(Modifier.weight(1f));time(Modifier.weight(1f))}
            }
            OutlinedTextField(draft.body,{v->vm.updateRecord{it.copy(body=v)}},Modifier.fillMaxWidth().testTag("record-body"),minLines=5,
                label={Text(if(draft.kind==RecordKind.VIEWING)"感受（可选）" else "想法与感受")},enabled=!busy,isError=errors.containsKey("body"),supportingText={errors["body"]?.let{Text(it)}})
            OutlinedTextField(draft.location,{v->vm.updateRecord{it.copy(location=v)}},Modifier.fillMaxWidth().testTag("record-location"),label={Text("地点（可选）")},singleLine=true,
                enabled=!busy,isError=errors.containsKey("location"),supportingText={errors["location"]?.let{Text(it)}})
        }
    }
}
