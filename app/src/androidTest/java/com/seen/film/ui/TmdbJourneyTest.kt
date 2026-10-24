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
        // This Application owns only an in-memory instrumentation database.
        runBlocking { withContext(Dispatchers.IO) { app.database.clearAllTables() } }
        app.getSharedPreferences("tmdb", 0).edit().clear().commit()
        waitFor("还没有电影")
        ui.onNodeWithContentDescription("添加看过的电影").performClick()
        ui.onNodeWithText("搜索片名或原名").performTextInput("Fixture")
        closeSoftKeyboard()
        ui.onNodeWithText("搜索 TMDB").performClick()
        waitFor("尚未配置 TMDB Read Access Token，仍可手动添加电影")
        ui.onNodeWithText("手动添加电影").assertIsEnabled()
        app.getSharedPreferences("tmdb", 0).edit().putString("token", "fixture-only").commit()
        ui.onNodeWithText("搜索片名或原名").performTextClearance()
        ui.onNodeWithText("搜索片名或原名").performTextInput("timeout")
        closeSoftKeyboard()
        ui.onNodeWithText("搜索 TMDB").performClick()
        waitFor("电影资料服务器连接超时，请稍后重试；仍可手动添加电影")
        ui.onNodeWithText("手动添加电影").assertIsEnabled()
        ui.onNodeWithText("搜索片名或原名").performTextClearance()
        ui.onNodeWithText("搜索片名或原名").performTextInput("Fixture")
        closeSoftKeyboard()
        ui.onNodeWithText("搜索 TMDB").performClick()
        waitFor("联网验收电影")
        ui.onNodeWithText("联网验收电影").performScrollTo().performClick()
        waitFor("电影名称 *")
        ui.activityRule.scenario.recreate()
        waitFor("电影名称 *")
        ui.onNodeWithText("保存").performClick()
        waitFor("看过 1 次")
        ui.onNodeWithText("导演 · 测试导演").assertExists()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("添加看过的电影").performClick()
        ui.onNodeWithText("搜索片名或原名").performTextInput("Fixture")
        closeSoftKeyboard()
        ui.onNodeWithText("搜索 TMDB").performClick()
        waitFor("TMDB · 第 1 页 · 资料来源 The Movie Database")
        ui.onAllNodesWithText("联网验收电影").onFirst().performScrollTo().performClick()
        waitFor("添加感受（可选）")
        ui.onNodeWithText("保存").performClick()
        waitFor("看过 2 次")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("海报").performClick()
        waitFor("1 部电影")
    }
}
