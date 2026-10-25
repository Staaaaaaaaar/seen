package com.seen.film.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.seen.film.MainActivity
import com.seen.film.SeenTestApplication
import com.seen.film.domain.ViewingDraft
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeleteMovieJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @After fun cleanUp() = runBlocking {
        val app = ui.activity.application as SeenTestApplication
        app.drafts.clear()
        app.credentials.save("")
        withContext(Dispatchers.IO) { app.database.clearAllTables() }
    }
    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun cancelThenDeleteMovieAndAllViewingsThenSearchAndImportAgain() {
        val app = ui.activity.application as SeenTestApplication
        val id = runBlocking {
            withContext(Dispatchers.IO) { app.database.clearAllTables() }
            app.credentials.save("fixture-token")
            val draft = ViewingDraft(title = "待删除电影", tmdbId = 4242, date = "2020-01-01")
            val movieId = app.repository.save(draft)
            app.repository.save(draft.copy(key = UUID.randomUUID().toString(), isNewMovie = false))
            app.repository.save(ViewingDraft(title = "保留电影", date = "2021-01-01"))
            movieId
        }
        waitFor("待删除电影")
        ui.onNodeWithText("待删除电影").performClick()
        waitFor("电影详情")
        ui.onNodeWithContentDescription("电影操作").performClick()
        ui.onNodeWithText("删除电影").performClick()
        waitFor("删除这部电影？")
        ui.onNodeWithText("将删除《待删除电影》及全部 2 次观看记录。此操作无法撤销，之后可重新搜索添加。").assertExists()
        ui.onNodeWithText("取消").performClick()
        assertNotNull(runBlocking { app.repository.movie(id) })
        ui.onNodeWithContentDescription("电影操作").performClick()
        ui.onNodeWithText("删除电影").performClick()
        ui.activityRule.scenario.recreate()
        waitFor("删除这部电影？")
        ui.onNodeWithText("删除电影").performClick()
        waitFor("电影库")
        ui.onNodeWithText("待删除电影").assertDoesNotExist()
        ui.onNodeWithText("保留电影").assertExists()
        assertNull(runBlocking { app.repository.movie(id) })
        assertTrue(runBlocking { app.database.dao().allViewings() }.none { it.movieId == id })
        ui.onNodeWithContentDescription("添加记录").performClick()
        ui.onNodeWithText("搜索片名或原名").performTextInput("Fixture")
        closeSoftKeyboard()
        ui.onNodeWithText("搜索 TMDB").performScrollTo().performClick()
        waitFor("联网验收电影")
        ui.onNodeWithText("联网验收电影").performScrollTo().performClick()
        waitFor("添加观看")
        ui.onNodeWithText("保存").performClick()
        waitFor("观看记录 · 1 次")
        val replacement = runBlocking { app.database.dao().movieByTmdbId(4242) }!!
        assertNotEquals(id, replacement.id)
        assertEquals(1, runBlocking { app.database.dao().allViewings() }.count { it.movieId == replacement.id })
    }
}
