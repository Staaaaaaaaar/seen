package com.seen.film.ui

import androidx.compose.ui.semantics.SemanticsActions
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

class NotesJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ui.activity.application as SeenTestApplication
    private val vm get()=ViewModelProvider(ui.activity)[SeenViewModel::class.java]
    private fun waitFor(text:String)=ui.waitUntil(15000){ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    @Before fun seed(){
        ui.waitUntil(15000){vm.draftReady.value&&!vm.busy.value}
        runBlocking{app.database.clearAllTables();app.repository.addMovie(ViewingDraft(title="Record film"))}
        waitFor("Record film");ui.onNodeWithText("Record film").performClick()
    }
    private fun add(kind:String){
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasContentDescription("新增记录或留言"))
        ui.onNodeWithContentDescription("新增记录或留言").performClick()
        ui.onNodeWithText(kind).performClick()
    }
    @Test fun notesHaveDatesAndPlacesWithoutCreatingAViewing(){
        add("一段想法")
        ui.onNodeWithTag("record-body").performTextInput("A thought 🎬")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("record-location").performScrollTo().performTextInput("Home")
        ui.onNodeWithTag("record-time").performScrollTo().performTextReplacement("20:15")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("record-body").assertTextContains("A thought 🎬")
        ui.onNodeWithText("保存").performClick();waitFor("A thought 🎬")
        val note=runBlocking{app.database.dao().allNotes().single()}
        assertEquals("Home",note.location);assertEquals("20:15",note.recordedTime)
        assertEquals(0,runBlocking{app.database.dao().viewingCount()})
        ui.onNodeWithText("A thought 🎬").assertIsDisplayed()
        ui.onNodeWithText("Home").assertIsDisplayed()
        ui.onNodeWithText("${note.recordedDate}  20:15").assertIsDisplayed()
    }
    @Test fun halfStarRatingAndAViewingRemainIndependent(){
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasTestTag("personal-rating"))
        ui.onNodeWithTag("personal-rating").performSemanticsAction(SemanticsActions.SetProgress){it(4.5f)}
        ui.waitUntil(15000){runBlocking{app.database.dao().allMovies().single().personalRatingHalfStars==9}}
        add("这次观看")
        ui.onNodeWithTag("record-body").performTextInput("Watched again")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithText("保存").performClick();waitFor("Watched again")
        assertEquals(1,runBlocking{app.database.dao().viewingCount()})
        assertEquals(9,runBlocking{app.database.dao().allMovies().single().personalRatingHalfStars})
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasTestTag("personal-rating"))
        ui.onNodeWithContentDescription("清除评分").performClick()
        ui.waitUntil(15000){runBlocking{app.database.dao().allMovies().single().personalRatingHalfStars==null}}
        assertEquals(1,runBlocking{app.database.dao().viewingCount()})
    }
    @Test fun emptyNoteCannotSaveAndBackKeepsDraftUntilExplicitDiscard(){
        add("一段想法");ui.onNodeWithText("保存").performClick();waitFor("请写一点想法")
        ui.onNodeWithTag("record-body").performTextInput("Keep draft")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithContentDescription("返回").performClick();waitFor("放弃未保存的修改？")
        ui.onNodeWithText("继续编辑").performClick()
        ui.onNodeWithTag("record-body").assertTextContains("Keep draft")
        assertTrue(runBlocking{app.database.dao().allNotes().isEmpty()})
        ui.onNodeWithContentDescription("返回").performClick();ui.onNodeWithText("放弃修改").performClick()
        ui.waitUntil(15000){vm.recordDraft.value==null&&!vm.busy.value}
        assertTrue(runBlocking{app.database.dao().allNotes().isEmpty()})
    }
    @Test fun recoveredDraftStillRequiresExplicitDiscard(){
        val id=runBlocking{app.database.dao().allMovies().single().id}
        runBlocking{app.drafts.writeRecord(RecordDraft(movieId=id,kind=RecordKind.NOTE,body="Recovered words",date="2020-01-01"))}
        val store=androidx.lifecycle.ViewModelStore()
        val owner=object:androidx.lifecycle.ViewModelStoreOwner{override val viewModelStore=store}
        var recovered:SeenViewModel?=null
        try{
            ui.runOnUiThread{recovered=ViewModelProvider(owner,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[SeenViewModel::class.java]}
            ui.waitUntil(15000){recovered?.draftReady?.value==true}
            assertEquals("Recovered words",recovered!!.recordDraft.value!!.body)
            assertTrue("Recovered unsaved input must not be discarded by a plain Back",recovered!!.isRecordDirty)
        }finally{ui.runOnUiThread{store.clear()};runBlocking{app.drafts.clearRecord()}}
    }

}
