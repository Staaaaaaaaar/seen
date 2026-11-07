package com.seen.film.ui
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.seen.film.MainActivity
import com.seen.film.SeenTestApplication
import com.seen.film.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class RefinementJourneyTest{
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(ui.activity)[SeenViewModel::class.java]
    @Test fun filtersAreCenteredAndSortDirectionsAreInline(){
        resetTestLibrary(ui,ui.activity.application as SeenTestApplication,vm)
        for(label in listOf("全部","已看","未看")){
            val text=ui.onNodeWithText(label,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val target=ui.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue(abs(text.center.x-target.center.x)<2f)
            ui.onNodeWithText(label).performClick()
        }
        ui.onNodeWithContentDescription("排序电影").performClick()
        ui.onNodeWithText("旧到新").assertDoesNotExist();ui.onNodeWithText("新到旧").assertDoesNotExist()
        ui.onNodeWithContentDescription("切换电影片名方向").performClick()
        ui.waitUntil(15000){vm.app.libraryPreferences.read().direction==SortDirection.DESC}
        ui.onNodeWithContentDescription("切换电影片名方向").performClick()
        ui.waitUntil(15000){vm.app.libraryPreferences.read().direction==SortDirection.ASC}
    }
    @Test fun addingOpensSearchWithoutManualOrSourceBrand(){
        resetTestLibrary(ui,ui.activity.application as SeenTestApplication,vm)
        ui.onNodeWithTag("bottom-add").performClick()
        ui.onNodeWithText("手动添加电影").assertDoesNotExist()
        ui.onNodeWithText("搜索 TMDB").assertDoesNotExist()
        ui.onNode(hasText("搜索") and hasClickAction()).assertExists()
    }
    @Test fun fullCreditsScrollWithoutAnExpandButtonOrDuplicateDirector(){
        val app=ui.activity.application as SeenTestApplication
        resetTestLibrary(ui,app,vm)
        kotlinx.coroutines.runBlocking{
            val id=app.repository.addMovie(ViewingDraft(title="演职测试",directorNames="测试导演"))
            app.database.dao().insertCredits(listOf(com.seen.film.data.MovieCredit(id,"director",100,"测试导演",false,job="Director",position=0))+(1..8).map{com.seen.film.data.MovieCredit(id,"actor-$it",it.toLong(),"演员 $it",true,position=it)})
        }
        ui.waitUntil(15000){ui.onAllNodesWithText("演职测试").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("演职测试").performClick()
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasTestTag("credits-list"))
        ui.onAllNodesWithText("测试导演").assertCountEquals(1)
        ui.onNodeWithContentDescription("展开演职人员").assertDoesNotExist()
        ui.onNodeWithTag("credits-list").performScrollToNode(hasText("演员 8"))
        ui.onNodeWithText("演员 8").assertIsDisplayed()
    }

    @Test fun emptyStatisticsStillHaveAZeroChart(){
        resetTestLibrary(ui,ui.activity.application as SeenTestApplication,vm)
        ui.onNodeWithTag("tab-统计").performClick()
        ui.waitUntil(15000){ui.onAllNodesWithTag("monthly-chart").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("statistics-movie-count").assertTextEquals("0")
        ui.onNodeWithTag("monthly-chart").assertExists()
        ui.onNodeWithTag("statistics-list").performScrollToNode(hasText("无记录"))
        ui.onNodeWithText("无记录").assertExists()
    }

}
