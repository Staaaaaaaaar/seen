package com.seen.film.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.seen.film.MainActivity
import com.seen.film.SeenTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MovieJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ui.activity.application as SeenTestApplication
    private val vm get()=ViewModelProvider(ui.activity)[SeenViewModel::class.java]
    private fun waitFor(text:String)=ui.waitUntil(15000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun addViewing(){
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasContentDescription("新增记录或留言"))
        ui.onNodeWithContentDescription("新增记录或留言").performClick();ui.onNodeWithText("这次观看").performClick()
    }
    private fun menu(){
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasContentDescription("观看记录操作",substring=true))
        ui.onAllNodes(hasContentDescription("观看记录操作",substring=true)).onFirst().performClick()
    }
    private fun count()=runBlocking{app.database.dao().viewingCount()}
    @Test fun manualMovieRatingRotationRepeatEditAndDelete(){
        resetTestLibrary(ui,app,vm)
        ui.onNodeWithContentDescription("添加影片").performClick();resumeLegacyMovieEditor(ui,vm)
        ui.onNodeWithText("电影名称 *").performTextInput("模拟器验收电影");closeSoftKeyboard();ui.onNodeWithText("保存").performClick()
        waitFor("模拟器验收电影");assertEquals(0,count())
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasTestTag("personal-rating"))
        for(halfStars in 1..10){
            ui.onNodeWithTag("personal-rating").performSemanticsAction(SemanticsActions.SetProgress){it(halfStars/2f)}
            ui.waitUntil(15000){!vm.busy.value&&runBlocking{app.database.dao().allMovies().single().personalRatingHalfStars==halfStars}}
        }
        ui.onNodeWithTag("personal-rating").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(0f)}
        ui.waitUntil(15000){!vm.busy.value&&runBlocking{app.database.dao().allMovies().single().personalRatingHalfStars==null}}
        ui.onNodeWithTag("personal-rating").performSemanticsAction(SemanticsActions.SetProgress){it(4.5f)}
        ui.waitUntil(15000){!vm.busy.value&&runBlocking{app.database.dao().allMovies().single().personalRatingHalfStars==9}}
        addViewing();ui.onNodeWithText("保存").performClick()
        ui.waitUntil(15000){!vm.busy.value&&count()==1}
        ui.activityRule.scenario.recreate()
        addViewing();ui.onNodeWithTag("record-body").performTextInput("重看后的感受 🎬\n第二行");closeSoftKeyboard()
        ui.activityRule.scenario.recreate();ui.onNodeWithTag("record-body").assertTextContains("重看后的感受 🎬\n第二行")
        ui.onNodeWithText("保存").performClick();waitFor("重看后的感受 🎬\n第二行")
        assertEquals(2,count())
        menu();ui.onNodeWithText("编辑").performClick()
        ui.onNodeWithTag("record-body").performTextReplacement("修改后的感受");closeSoftKeyboard();ui.onNodeWithText("保存").performClick()
        waitFor("修改后的感受")
        menu();ui.onNodeWithText("删除").performClick();ui.onNodeWithText("取消").performClick();assertEquals(2,count())
        repeat(2){index->
            menu();ui.onNodeWithText("删除").performClick();ui.onNodeWithText("删除记录").performClick()
            ui.waitUntil(15000){!vm.busy.value&&count()==1-index}
        }
        assertEquals(9,runBlocking{app.database.dao().allMovies().single().personalRatingHalfStars})
        ui.onNodeWithContentDescription("返回").performClick();waitFor("模拟器验收电影")
        ui.onNodeWithText("已看").performClick();waitFor("没有匹配的电影")
        ui.onNodeWithText("未看").performClick();waitFor("模拟器验收电影")
    }
}
