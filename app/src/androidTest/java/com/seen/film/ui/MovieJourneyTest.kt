package com.seen.film.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seen.film.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MovieJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) {
        ui.waitUntil(timeoutMillis = 15_000) {
            ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun recordMenu() {
        ui.onNodeWithTag("movie-detail-list").performScrollToIndex(3)
        ui.onAllNodes(hasContentDescription("观看记录操作", substring = true))[0].performClick()
    }
    private fun deleteFirst() {
        recordMenu()
        ui.onNodeWithText("删除本次观看").performClick()
        ui.onNodeWithText("删除记录").performClick()
        ui.onNodeWithTag("movie-detail-list").performScrollToIndex(2)
    }

    @Test fun manualMovieRatingRotationRepeatEditAndDelete() {
        waitFor("还没有电影")
        ui.onNodeWithContentDescription("添加记录").performClick()
        waitFor("手动添加电影")
        ui.onNodeWithText("手动添加电影").performClick()
        waitFor("电影名称 *")
        ui.onNodeWithText("电影名称 *").performTextInput("模拟器验收电影")
        closeSoftKeyboard()
        ui.onNodeWithText("下一步").performClick()

        // Exercise the actual UI controls, including the previously disabled '+'.
        for (halfStars in 1..10) {
            ui.onNodeWithContentDescription("增加半星").performScrollTo().assertIsEnabled().performClick()
            waitFor("个人评分 · ${String.format(Locale.ROOT, "%.1f", halfStars / 2.0)}")
        }
        ui.onNodeWithContentDescription("增加半星").assertIsNotEnabled()
        repeat(9) { ui.onNodeWithContentDescription("减少半星").performClick() }
        ui.onNodeWithContentDescription("减少半星").assertIsNotEnabled()
        ui.onNodeWithText("清除评分").performScrollTo().performClick()
        waitFor("个人评分 · 未评分")
        ui.onNodeWithText("保存").performClick()
        waitFor("观看记录 · 1 次")
        ui.onNodeWithText("感受").assertDoesNotExist()

        ui.activityRule.scenario.recreate()
        waitFor("观看记录 · 1 次")
        ui.onNodeWithText("再看一次").performClick()
        waitFor("添加感受（可选）")
        ui.onNodeWithText("添加感受（可选）").performScrollTo().performClick()
        ui.onNodeWithText("想法与感受").performScrollTo().performTextInput("重看后的感受 🎬\n第二行")
        closeSoftKeyboard()
        ui.activityRule.scenario.recreate()
        waitFor("重看后的感受 🎬\n第二行")
        ui.onNodeWithText("保存").performClick()
        waitFor("观看记录 · 2 次")

        ui.onNodeWithContentDescription("返回").performClick()
        waitFor("记录")
        ui.onNodeWithText("时间").assertDoesNotExist()
        ui.onNodeWithText("2 次观看").assertExists()
        ui.onAllNodesWithText("模拟器验收电影")[0].performClick()
        waitFor("重看后的感受 🎬\n第二行")
        recordMenu()
        ui.onNodeWithText("编辑观看记录").performClick()
        waitFor("编辑观看")
        ui.onNodeWithText("想法与感受").performScrollTo().performTextClearance()
        ui.onNodeWithText("想法与感受").performTextInput("修改后的感受")
        closeSoftKeyboard()
        ui.onNodeWithText("保存").performClick()
        waitFor("观看记录 · 2 次")
        ui.onNodeWithText("观看记录 · 2 次").performScrollTo()
        recordMenu()
        ui.onNodeWithText("删除本次观看").performClick()
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithTag("movie-detail-list").performScrollToIndex(2)
        ui.onNodeWithText("观看记录 · 2 次").assertExists()
        deleteFirst()
        waitFor("观看记录 · 1 次")
        deleteFirst()
        waitFor("暂无观看记录")
        ui.onNodeWithContentDescription("返回").performClick()
        waitFor("还没有电影")
    }
}
