package com.seen.film.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.seen.film.MainActivity
import com.seen.film.SeenTestApplication
import com.seen.film.data.*
import com.seen.film.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream

/** Synthetic-only screenshots. Run manually to retain output before Gradle removes test APKs. */
class VisualReviewTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ui.activity.application as SeenTestApplication
    private val vm get()=ViewModelProvider(ui.activity)[SeenViewModel::class.java]
    private fun capture(name:String){
        ui.onRoot().captureToImage()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir("ui-review"),"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun libraryDetailsRecordsStatisticsAndEditor(){
        resetTestLibrary(ui,app,vm)
        runBlocking{
            val bitmap=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);canvas.drawColor(0xff284955.toInt())
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xffd9c29a.toInt()}
            canvas.drawCircle(420f,200f,90f,paint)
            paint.color=0xff67887d.toInt()
            for(i in 0..5)canvas.drawRect(i*120f,480f-(i%2)*80,80+i*120f,900f,paint)
            val bytes=ByteArrayOutputStream().apply{bitmap.compress(Bitmap.CompressFormat.JPEG,90,this)}.toByteArray();bitmap.recycle()
            val poster=app.posters.importDownloaded(bytes)
            val id=app.repository.addMovie(ViewingDraft(title="完美的日子",originalTitle="Perfect Days",year="2023",posterKey=poster))
            val movie=app.database.dao().movie(id)!!
            app.database.dao().updateLocalizedMovie(id,movie.title,movie.originalTitle,2023,null,"维姆·文德斯",124,"日本 / 德国","在东京，平山日复一日地工作。他喜欢音乐、书籍和树木的光影。那些看似相同的日常，藏着细微而珍贵的变化。",poster,"zh-CN",movie.updatedAt)
            app.repository.setPersonalRating(id,9)
            app.repository.saveRecord(RecordDraft(movieId=id,kind=RecordKind.VIEWING,date="2026-01-12",time="20:15",location="家中",body="光影落在树叶上，时间慢了下来。"))
            app.repository.saveRecord(RecordDraft(movieId=id,kind=RecordKind.NOTE,date="2026-01-12",time="22:30",location="窗边",body="也许下一次重看，会留意更多平凡的瞬间。"))
            app.database.dao().insertGenres(listOf(MovieGenre(id,18,"剧情",0),MovieGenre(id,35,"喜剧",1)))
            app.database.dao().insertCredits((0..7).map{MovieCredit(id,"cast-$it",it+1L,listOf("役所广司","柄本时生","中野有纱","山田葵","麻生祐未","石川小百合","三浦友和","田中泯")[it],true,character="演员 / 角色 ${it+1}",position=it)})
            listOf("花样年华","海街日记","晒后假日","漫长的电影名称：一次跨越山海的旅行 🎬").forEachIndexed{i,title->app.repository.addMovie(ViewingDraft(title=title,year="${2022-i}"))}
        }
        ui.waitUntil(15000){vm.library.value?.size==5}
        ui.onNodeWithText("完美的日子").assertExists();capture("library")
        ui.onNodeWithText("完美的日子").performClick();capture("detail-hero")
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasText("光影落在树叶上，时间慢了下来。"));capture("detail-records")
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasContentDescription("新增记录或留言"))
        ui.onNodeWithContentDescription("新增记录或留言").performClick();ui.onNodeWithText("一段想法").performClick()
        ui.onNodeWithTag("record-body").performTextInput("保留此刻的想法");capture("editor-keyboard")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithContentDescription("返回").performClick();ui.onNodeWithText("放弃修改").performClick()
        ui.onNodeWithContentDescription("返回").performClick();ui.onNodeWithTag("tab-统计").performClick()
        ui.waitUntil(15000){ui.onAllNodesWithTag("statistics-movie-count").fetchSemanticsNodes().isNotEmpty()}
        val statusTop=androidx.core.view.ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        org.junit.Assert.assertTrue("Root screen must restore top system inset after details",ui.onNodeWithText("Seen").fetchSemanticsNode().boundsInRoot.top>=statusTop)
        capture("statistics")
        ui.onNodeWithContentDescription("设置").performClick();ui.onNodeWithText("资料语言").performClick();capture("language")
    }
}
