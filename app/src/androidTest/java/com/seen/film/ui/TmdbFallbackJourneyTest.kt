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
class TmdbFallbackJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun search(value: String) {
        ui.onNodeWithText("搜索片名或原名").performScrollTo().performTextReplacement(value)
        closeSoftKeyboard()
        ui.onNodeWithText("搜索 TMDB").performScrollTo().performClick()
    }

    @Test fun emptyUnauthorizedPagingAndPosterFailureKeepRecordingAvailable() {
        val app = ui.activity.application as SeenTestApplication
        runBlocking { withContext(Dispatchers.IO) { app.database.clearAllTables() }; app.credentials.save("fixture.token") }
        waitFor("还没有电影")
        ui.onNodeWithContentDescription("添加看过的电影").performClick()
        search("empty")
        waitFor("没有找到电影，可修改关键词或手动添加。")
        ui.onNodeWithText("手动添加电影").assertIsEnabled()
        search("authfailure")
        waitFor("TMDB 凭据无效或无权限，请在设置中检查；仍可手动添加")
        ui.onNodeWithText("手动添加电影").assertIsEnabled()
        search("Fixture")
        waitFor("联网验收电影")
        ui.onNodeWithText("下一页").performScrollTo().performClick()
        waitFor("TMDB · 第 2 页 · 资料来源 The Movie Database")
        ui.onNodeWithText("第二页电影").assertExists()
        ui.onNodeWithText("上一页").performScrollTo().performClick()
        waitFor("联网验收电影")
        search("posterfailure")
        waitFor("海报失败验收电影")
        ui.onNodeWithText("海报失败验收电影").performScrollTo().performClick()
        waitFor("电影名称 *")
        ui.onNodeWithText("选择海报（可选）").performScrollTo().assertExists()
        ui.onNodeWithText("保存").performClick()
        waitFor("看过 1 次")
        ui.onNodeWithText("海报失败验收电影").assertExists()
    }
}
