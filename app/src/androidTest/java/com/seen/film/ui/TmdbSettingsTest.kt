package com.seen.film.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seen.film.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TmdbSettingsTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun credentialValidationRetainsInputAndSaveRemovalSurviveRotation() {
        waitFor("Seen")
        ui.onNodeWithContentDescription("更多").performClick()
        ui.onNodeWithText("设置与关于").performClick()
        ui.onNodeWithText("移除凭据").performScrollTo().performClick()
        waitFor("未配置；可继续手动记录")
        ui.onNodeWithText("API Read Access Token").performScrollTo().performTextInput("bad token")
        closeSoftKeyboard()
        ui.onNodeWithText("保存凭据").performScrollTo().performClick()
        waitFor("令牌格式无效，请复制完整的 API Read Access Token，不要添加 Bearer 前缀")
        ui.onNodeWithText("API Read Access Token").assertTextContains("bad token")
        ui.onNodeWithText("API Read Access Token").performScrollTo().performTextReplacement("fixture.valid.token")
        closeSoftKeyboard()
        ui.onNodeWithText("保存凭据").performScrollTo().performClick()
        waitFor("已配置访问凭据")
        ui.activityRule.scenario.recreate()
        waitFor("已配置访问凭据")
        ui.onNodeWithText("移除凭据").performScrollTo().performClick()
        waitFor("未配置；可继续手动记录")
        ui.onNodeWithText("This product uses the TMDB API but is not endorsed or certified by TMDB.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("The Movie Database (TMDB)").assertExists()
    }
}
