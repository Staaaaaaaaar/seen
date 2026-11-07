package com.seen.film.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seen.film.MainActivity
import com.seen.film.SeenTestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TmdbJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun missingCredentialTimeoutSelectionRotationAndReuse() {
        val app = ui.activity.application as SeenTestApplication
        resetTestLibrary(ui,app,androidx.lifecycle.ViewModelProvider(ui.activity)[SeenViewModel::class.java])
        // This Application owns only an in-memory instrumentation database.
        runBlocking { withContext(Dispatchers.IO) { app.database.clearAllTables() } }
        app.getSharedPreferences("tmdb", 0).edit().clear().commit()
        waitFor("还没有电影")
        ui.onNodeWithContentDescription("添加影片").performClick()
        ui.onNodeWithText("搜索片名或原名").performTextInput("Fixture")
        closeSoftKeyboard()
        ui.onNode(hasText("搜索") and hasClickAction()).performClick()
        waitFor("请先在设置中配置电影资料访问凭据")
        ui.onNodeWithText("手动添加电影").assertDoesNotExist()
        app.getSharedPreferences("tmdb", 0).edit().putString("token", "fixture-only").commit()
        ui.onNodeWithText("搜索片名或原名").performTextClearance()
        ui.onNodeWithText("搜索片名或原名").performTextInput("timeout")
        closeSoftKeyboard()
        ui.onNode(hasText("搜索") and hasClickAction()).performClick()
        waitFor("电影资料服务器连接超时，请稍后重试")
        ui.onNodeWithText("手动添加电影").assertDoesNotExist()
        ui.onNodeWithText("搜索片名或原名").performTextClearance()
        ui.onNodeWithText("搜索片名或原名").performTextInput("Fixture")
        closeSoftKeyboard()
        ui.onNode(hasText("搜索") and hasClickAction()).performClick()
        waitFor("联网验收电影")
        ui.onNodeWithText("联网验收电影").performScrollTo().performClick()
        waitFor("添加影片")
        ui.activityRule.scenario.recreate()
        waitFor("添加影片")
        ui.onNodeWithText("保存").performClick()
        waitFor("联网验收电影")
        org.junit.Assert.assertEquals(0,runBlocking{app.database.dao().viewingCount()})
        ui.onNodeWithTag("movie-detail-list").performScrollToIndex(0)
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasText("测试导演"))
        ui.onNodeWithText("测试导演").assertExists()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("添加影片").performClick()
        ui.onNodeWithText("搜索片名或原名").performTextInput("Fixture")
        closeSoftKeyboard()
        ui.onNode(hasText("搜索") and hasClickAction()).performClick()
        waitFor("第 1 页")
        ui.onAllNodesWithText("联网验收电影").onFirst().performScrollTo().performClick()
        waitFor("联网验收电影")
        org.junit.Assert.assertEquals(1,runBlocking{app.database.dao().movieCount()})
        org.junit.Assert.assertEquals(0,runBlocking{app.database.dao().viewingCount()})

    }
}
