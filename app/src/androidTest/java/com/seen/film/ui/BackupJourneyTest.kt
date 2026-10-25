package com.seen.film.ui

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seen.film.MainActivity
import com.seen.film.SeenApplication
import com.seen.film.data.BackupCodec
import com.seen.film.data.BackupData
import com.seen.film.domain.ViewingDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = ui.waitUntil(15000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    @Test fun previewSurvivesRotationCancelPreservesLibraryAndExplicitRestoreReplacesIt() {
        waitFor("电影库")
        val app = ui.activity.application as SeenApplication
        val vm = ViewModelProvider(ui.activity)[SeenViewModel::class.java]
        ui.waitUntil { vm.draftReady.value }
        ui.runOnIdle { if (vm.draft.value != null) vm.discard {} }
        ui.waitUntil { !vm.busy.value && vm.draft.value == null }
        val empty = File(app.cacheDir, "backup-ui-empty.json")
        runBlocking {
            app.repository.save(ViewingDraft(title = "覆盖前的合成记录", date = "2000-01-01"))
            BackupCodec().write(empty, BackupData("0.3.0", "2020-01-01T00:00:00Z", emptyList(), emptyList(), emptyMap()))
        }
        ui.onNodeWithText("设置").performClick()
        ui.onNodeWithText("导入备份").performClick()
        ui.onNodeWithText("备份与恢复").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { vm.previewBackup(Uri.fromFile(empty)) }
        waitFor("确认覆盖恢复")
        ui.onNodeWithText("先导出现有数据").performScrollTo().assertIsEnabled()
        ui.activityRule.scenario.recreate()
        waitFor("确认覆盖恢复")
        ui.onNodeWithText("取消恢复").performScrollTo().performClick()
        waitFor("已取消恢复")
        ui.onNodeWithText("已取消恢复").assertIsDisplayed()
        runBlocking { assertTrue(app.database.dao().allMovies().any { it.title == "覆盖前的合成记录" }) }
        ui.runOnIdle { ViewModelProvider(ui.activity)[SeenViewModel::class.java].previewBackup(Uri.fromFile(empty)) }
        waitFor("确认覆盖恢复")
        ui.onNodeWithText("覆盖恢复").performScrollTo().performClick()
        waitFor("恢复已完成")
        ui.onNodeWithText("恢复已完成").assertIsDisplayed()
        runBlocking { assertEquals(0, app.database.dao().movieCount()); assertEquals(0, app.database.dao().viewingCount()) }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("选择备份恢复").performScrollTo().assertIsEnabled()
        ui.onNodeWithText("确认覆盖恢复").assertDoesNotExist()
        empty.delete()
    }
    @Test fun lostExportSessionReportsFailureAndPreservesExistingData() {
        waitFor("电影库")
        val app = ui.activity.application as SeenApplication
        val vm = ViewModelProvider(ui.activity)[SeenViewModel::class.java]
        val before = runBlocking { app.database.dao().allMovies() to app.database.dao().allViewings() }
        ui.runOnIdle { vm.finishExport(Uri.parse("content://unavailable/new-document")) }
        waitFor("导出会话已结束，请重新导出；所选位置可能留下空文件")
        runBlocking { assertEquals(before, app.database.dao().allMovies() to app.database.dao().allViewings()) }
        ui.runOnIdle { vm.clearError() }
    }

}
