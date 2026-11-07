package com.seen.film.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.seen.film.MainActivity
import com.seen.film.SeenTestApplication
import com.seen.film.data.*
import com.seen.film.domain.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StatisticsJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ui.activity.application as SeenTestApplication
    private val vm get()=ViewModelProvider(ui.activity)[SeenViewModel::class.java]
    @Before fun reset(){resetTestLibrary(ui,app,vm)}
    @Test fun statisticsCountViewingsAndUniqueMoviesWithoutNotes(){
        runBlocking{
            val id=app.repository.addMovie(ViewingDraft(title="Stats film"))
            app.repository.saveRecord(RecordDraft(movieId=id,kind=RecordKind.VIEWING))
            app.repository.saveRecord(RecordDraft(movieId=id,kind=RecordKind.VIEWING,date="2020-01-01"))
            app.repository.saveRecord(RecordDraft(movieId=id,kind=RecordKind.NOTE,body="No viewing"))
            app.database.dao().insertGenres((1L..6L).map{MovieGenre(id,it,"类型 $it",it.toInt())})
        }
        ui.waitUntil(15000){vm.library.value?.singleOrNull()?.viewings?.size==2}
        ui.onNodeWithTag("tab-统计").performClick()
        ui.waitUntil(15000){ui.onAllNodesWithTag("statistics-movie-count").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("statistics-movie-count").assertTextEquals("1")
        ui.onNodeWithTag("statistics-viewing-count").assertTextEquals("2")
        ui.onNodeWithContentDescription("搜索影库").assertDoesNotExist()
        ui.onNodeWithTag("bottom-add").assertDoesNotExist()
        ui.onNodeWithText("全部").assertDoesNotExist()
        ui.onNodeWithText("今年").assertDoesNotExist()
        ui.onNodeWithTag("statistics-viewing-count").assertTextEquals("2")
        ui.onNodeWithTag("statistics-list").performScrollToNode(hasText("展开全部类型"))
        ui.onNodeWithText("展开全部类型").performClick()
        ui.onNodeWithTag("statistics-list").performScrollToNode(hasText("类型 6"))
        ui.onNodeWithText("类型 6").assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("statistics-viewing-count").assertTextEquals("2")
    }
}
