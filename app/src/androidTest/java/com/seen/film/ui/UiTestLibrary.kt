package com.seen.film.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.seen.film.SeenApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Test-only reset of the instrumentation Application's synthetic data. */
internal fun resetTestLibrary(ui:ComposeTestRule,app:SeenApplication,vm:SeenViewModel){
    ui.waitUntil(15000){vm.draftReady.value&&!vm.busy.value}
    ui.runOnIdle{vm.discard{}}
    ui.waitUntil(15000){!vm.busy.value&&vm.draft.value==null}
    ui.runOnIdle{vm.discardRecord{}}
    ui.waitUntil(15000){!vm.busy.value&&vm.recordDraft.value==null}
    runBlocking{withContext(Dispatchers.IO){app.database.clearAllTables()}}
    ui.onNodeWithText("全部").performClick()
    ui.onNodeWithContentDescription("排序电影").performClick()
    ui.onAllNodesWithText("电影片名").onLast().performClick()
    ui.waitUntil(15000){ui.onAllNodesWithText("还没有电影").fetchSemanticsNodes().isNotEmpty()}
}

internal fun resumeLegacyMovieEditor(ui:ComposeTestRule,vm:SeenViewModel){
    androidx.test.espresso.Espresso.pressBack()
    ui.runOnIdle{vm.openMovieEditor{}}
    ui.waitUntil(15000){vm.draft.value!=null&&!vm.busy.value}
    ui.onNodeWithTag("bottom-add").performClick()
    ui.onNodeWithText("继续编辑").performClick()
}
