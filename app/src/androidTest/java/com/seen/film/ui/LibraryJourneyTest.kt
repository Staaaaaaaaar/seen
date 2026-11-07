package com.seen.film.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.seen.film.MainActivity
import com.seen.film.SeenTestApplication
import com.seen.film.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ui.activity.application as SeenTestApplication
    private val vm get()=ViewModelProvider(ui.activity)[SeenViewModel::class.java]
    private fun waitFor(text:String)=ui.waitUntil(15000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    @Before fun seed(){
        ui.waitUntil(15000){vm.draftReady.value&&!vm.busy.value}
        runBlocking {
            app.database.clearAllTables()
            val a=app.repository.addMovie(ViewingDraft(title="Alpha",year="2020"))
            app.repository.setPersonalRating(a,9)
            val b=app.repository.addMovie(ViewingDraft(title="Beta",year="2021"))
            app.repository.saveRecord(RecordDraft(movieId=b,kind=RecordKind.VIEWING,date="2020-01-01"))
        }
        waitFor("Alpha")
    }
    @Test fun filtersSortingAndSearchUseTheUnifiedLibrary(){
        ui.onNodeWithTag("tab-影库").assertExists();ui.onNodeWithTag("tab-统计").assertExists()
        ui.onNodeWithTag("tab-片单").assertDoesNotExist()
        ui.onNodeWithText("已看").performClick();ui.onNodeWithText("Beta").assertExists();ui.onNodeWithText("Alpha").assertDoesNotExist()
        ui.onNodeWithText("未看").performClick();ui.onNodeWithText("Alpha").assertExists();ui.onNodeWithText("Beta").assertDoesNotExist()
        ui.onNodeWithText("全部").performClick()
        ui.onNodeWithContentDescription("排序电影").performClick()
        ui.onAllNodesWithText("个人评分").onLast().performClick()
        ui.onNodeWithContentDescription("搜索影库").performClick()
        ui.onNodeWithTag("library-search").performTextInput("Alpha")
        ui.onNodeWithText("Beta").assertDoesNotExist()
        ui.onNodeWithContentDescription("设置").performClick()
        waitFor("访问凭据")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithTag("library-search").assertTextContains("Alpha")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("library-search").assertTextContains("Alpha")
        ui.onNodeWithTag("tab-统计").performClick()
        ui.onNodeWithContentDescription("搜索影库").assertDoesNotExist()
        ui.onNodeWithTag("bottom-add").assertDoesNotExist()
    }
    @Test fun addingAMovieDoesNotForceAViewingOrRating(){
        ui.onNodeWithTag("bottom-add").performClick()
        resumeLegacyMovieEditor(ui,vm)
        ui.onNodeWithText("电影名称 *").performTextInput("Only a movie")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithText("保存").performClick()
        waitFor("Only a movie")
        val movie=runBlocking{app.database.dao().allMovies().single{it.title=="Only a movie"}}
        assertNull(movie.personalRatingHalfStars)
        assertTrue(runBlocking{app.database.dao().allViewings().none{it.movieId==movie.id}})
    }
}
