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

class MetadataLanguageJourneyTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ui.activity.application as SeenTestApplication
    private val vm get()=ViewModelProvider(ui.activity)[SeenViewModel::class.java]
    @Before fun reset(){resetTestLibrary(ui,app,vm);ui.runOnIdle{vm.saveCredential("fixture-token"){}};ui.waitUntil(15000){vm.credentialConfigured.value&&!vm.busy.value&&vm.metadataReady.value}}
    @Test fun settingPublishesLanguageAndKeepsPersonalContent(){
        val id=runBlocking{
            val movie=app.repository.addMovie(ViewingDraft(title="Old title",tmdbId=4242))
            app.repository.setPersonalRating(movie,9)
            app.repository.saveRecord(RecordDraft(movieId=movie,kind=RecordKind.NOTE,body="My words",location="Home"))
            movie
        }
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("资料语言").performClick()
        ui.onNodeWithText("English").performClick()
        ui.waitUntil(15000){vm.librarySnapshot.value?.metadata?.activeLanguage=="en-US"}
        ui.onNodeWithText("当前：English").assertExists()
        val movie=runBlocking{app.repository.movie(id)}!!
        assertEquals(9,movie.personalRatingHalfStars)
        assertEquals("My words",runBlocking{app.database.dao().allNotes().single().body})
        assertEquals(0,runBlocking{app.database.dao().viewingCount()})
        ui.onNodeWithText("更新电影资料").assertExists()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("当前：English").assertExists()
    }
    @Test fun preparationCanBeCancelledAndRetriedWithoutBlockingPersonalWrites(){
        val id=runBlocking{app.repository.addMovie(ViewingDraft(title="Keep old",tmdbId=4242))}
        app.holdMetadata=true
        try{
            ui.onNodeWithContentDescription("设置").performClick()
            ui.onNodeWithText("资料语言").performClick()
            ui.onNodeWithText("English").performClick()
            ui.waitUntil(15000){vm.metadataRefreshState.value is com.seen.film.data.MetadataRefreshState.Preparing}
            ui.onNodeWithText("当前：简体中文").assertExists()
            assertFalse(vm.busy.value)
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithTag("bottom-add").assertIsNotEnabled()
            ui.onNodeWithText("Keep old").performClick()
            ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasTestTag("personal-rating"))
            ui.onNodeWithTag("personal-rating").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(3.5f)}
            ui.waitUntil(15000){!vm.busy.value&&runBlocking{app.repository.movie(id)?.personalRatingHalfStars==7}}
            ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasContentDescription("新增记录或留言"))
            ui.onNodeWithContentDescription("新增记录或留言").performClick()
            ui.onNodeWithText("一段想法").performClick()
            ui.onNodeWithTag("record-body").performTextInput("During preparation")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.onNodeWithText("保存").performClick()
            ui.waitUntil(15000){runBlocking{app.database.dao().noteCount()==1}}
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithContentDescription("设置").performClick()
            ui.onNodeWithText("资料语言").performClick()
            ui.onNodeWithText("取消准备").performClick()
        }finally{app.holdMetadata=false}
        ui.waitUntil(15000){!vm.metadataPreparing}
        assertEquals("zh-CN",vm.librarySnapshot.value!!.metadata.activeLanguage)
        assertEquals("Keep old",runBlocking{app.repository.movie(id)!!.title})
        app.failMetadata=true
        try{
            ui.onNodeWithText("English").performClick()
            ui.waitUntil(15000){vm.metadataRefreshState.value is com.seen.film.data.MetadataRefreshState.Failed}
            assertEquals("zh-CN",vm.librarySnapshot.value!!.metadata.activeLanguage)
        }finally{app.failMetadata=false}
        ui.onNodeWithText("English").performClick()
        ui.waitUntil(15000){vm.librarySnapshot.value?.metadata?.activeLanguage=="en-US"}
        assertEquals(7,runBlocking{app.repository.movie(id)!!.personalRatingHalfStars})
        assertEquals("During preparation",runBlocking{app.database.dao().allNotes().single().body})
    }

}
