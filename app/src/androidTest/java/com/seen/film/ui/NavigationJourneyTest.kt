package com.seen.film.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seen.film.MainActivity
import com.seen.film.SeenTestApplication
import com.seen.film.domain.ViewingDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class NavigationJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ui.activity.application as SeenTestApplication
    private val vm get() = ViewModelProvider(ui.activity)[SeenViewModel::class.java]
    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Before fun isolatedLibrary() {
        ui.waitUntil(15_000) { vm.draftReady.value && !vm.busy.value }
        ui.runOnIdle { vm.discard {} }
        ui.waitUntil(15_000) { !vm.busy.value && vm.draft.value == null }
        runBlocking { withContext(Dispatchers.IO) { app.database.clearAllTables() } }
        waitFor("还没有电影")
    }

    @Test fun bottomActionReturnsToSourceAndSettingsBackReturnsToLibrary() {
        ui.onNodeWithText("设置").performClick()
        ui.onNodeWithText("TMDB 凭证").assertExists()
        ui.onNodeWithText("导出数据").assertExists()
        ui.onNodeWithText("导入备份").assertExists()
        ui.onNodeWithTag("bottom-add").performClick()
        waitFor("选择电影")
        ui.onNodeWithTag("bottom-add").assertDoesNotExist()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("导入备份").assertExists()
        pressBack()
        waitFor("还没有电影")
        ui.onNodeWithText("时间").assertDoesNotExist()
        ui.onNodeWithText("Seen").assertDoesNotExist()
    }

    @Test fun manualStepsRetainDraftAndCancelLeavesNoEmptyMovie() {
        ui.onNodeWithTag("bottom-add").performClick()
        ui.onNodeWithText("手动添加电影").performClick()
        ui.onNodeWithText("下一步").performClick()
        waitFor("请输入电影名称")
        ui.onNodeWithText("电影名称 *").performTextInput("草稿往返 🎬")
        closeSoftKeyboard()
        ui.onNodeWithText("下一步").performClick()
        waitFor("添加观看")
        ui.activityRule.scenario.recreate()
        waitFor("添加观看")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("电影名称 *").assertTextContains("草稿往返 🎬")
        ui.onNodeWithContentDescription("返回").performClick()
        waitFor("放弃未保存的修改？")
        ui.onNodeWithText("继续编辑").performClick()
        ui.onNodeWithText("电影名称 *").assertTextContains("草稿往返 🎬")
        assertEquals(0, runBlocking { app.database.dao().movieCount() })
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("放弃修改").performClick()
        waitFor("选择电影")
        ui.onNodeWithContentDescription("返回").performClick()
        waitFor("还没有电影")
        assertEquals(0, runBlocking { app.database.dao().viewingCount() })
    }

    @Test fun existingDraftRequiresExplicitChoiceBeforeStartingAnotherRecord() {
        ui.runOnIdle { vm.openEditor { } }
        ui.waitUntil(15_000) { vm.draft.value != null && !vm.busy.value }
        val original = vm.draft.value!!.key
        ui.runOnIdle { vm.update { it.copy(title = "保留原草稿") } }
        ui.onNodeWithTag("bottom-add").performClick()
        waitFor("有未完成的编辑")
        ui.onNodeWithText("取消").performClick()
        assertEquals(original, vm.draft.value!!.key)
        ui.onNodeWithTag("bottom-add").performClick()
        ui.onNodeWithText("继续编辑").performClick()
        ui.onNodeWithText("电影名称 *").assertTextContains("保留原草稿")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("放弃修改").performClick()
        waitFor("还没有电影")
    }

    private fun screenshot(name: String) {
        ui.waitUntil(15_000) { name == "library" || name == "settings" || ui.onAllNodesWithTag("poster-loaded").fetchSemanticsNodes().isNotEmpty() }
        ui.onRoot().captureToImage() // Synchronize the rendered frame before the full-device screenshot.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(app.getExternalFilesDir("ui-review"), "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun posterLibrarySearchSurvivesSettingsAndSaveFocusesOlderViewing() {
        val titles = listOf("完美的日子", "海街日记", "花样年华", "步履不停", "晒后假日", "漫长的电影名称：一次跨越山海的旅行 🎬")
        val colors = listOf(0xff244653, 0xff60877d, 0xff733c43, 0xff9b8159, 0xff455c76, 0xff746984)
        var firstMovie = ""
        runBlocking {
            titles.forEachIndexed { i, title ->
                val bitmap = Bitmap.createBitmap(240, 360, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap); canvas.drawColor(colors[i].toInt())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffe4cda3.toInt() }
                canvas.drawCircle(164f, 92f, 36f, paint)
                paint.color = 0xffa6b9b0.toInt()
                for (j in 0..3) canvas.drawRect(20f + j * 54, 190f - (j % 2) * 35, 52f + j * 54, 324f, paint)
                val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, this) }.toByteArray()
                bitmap.recycle()
                val key = if (i == 5) null else app.posters.importDownloaded(bytes)
                val draft = ViewingDraft(title = title, year = "${2023 - i}", posterKey = key, date = "2001-01-0${i+1}", rating = 9)
                app.repository.save(draft)
                if (i == 0) firstMovie = draft.movieId
            }
        }
        ui.waitUntil(15_000) { vm.library.value?.size == 6 }
        waitFor(titles.last())
        screenshot("library")
        ui.onNodeWithContentDescription("搜索我的电影").performClick()
        ui.onNodeWithTag("library-search").performTextInput("完美")
        closeSoftKeyboard()
        ui.onNodeWithText("设置").performClick()
        screenshot("settings")
        ui.onNodeWithText("电影库").performClick()
        ui.onNodeWithTag("library-search").assertTextContains("完美")
        ui.onNodeWithText("完美的日子").performClick()
        screenshot("movie")
        ui.onNodeWithTag("movie-detail-list").performScrollToNode(hasText("再看一次"))
        ui.onNodeWithText("再看一次").performClick()
        screenshot("editor")
        // An older date must focus this new record rather than the first chronological entry.
        ui.runOnIdle { vm.update { it.copy(date = "1999-01-01", notes = "本次补记需要自动定位", rating = null) } }
        ui.onNodeWithText("保存").performClick()
        waitFor("本次补记需要自动定位")
        ui.onNodeWithText("本次补记需要自动定位").assertIsDisplayed()
        ui.onNodeWithText("收起感受").assertIsDisplayed()
        assertEquals(6, runBlocking { app.database.dao().movieCount() })
        assertEquals(2, vm.library.value!!.first { it.movie.id == firstMovie }.viewings.size)
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithTag("library-search").assertTextContains("完美")
        ui.onNodeWithText("完美的日子").assertIsDisplayed()
    }
}
