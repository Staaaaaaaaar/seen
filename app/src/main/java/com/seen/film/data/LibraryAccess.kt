package com.seen.film.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One application-wide gate for library writes, drafts and backup snapshots. */
class LibraryAccess {
    private val mutex = Mutex()
    private var preview: Any? = null
    suspend fun <T> write(block: suspend () -> T): T = mutex.withLock {
        check(preview == null) { "请先完成或取消备份恢复，再编辑电影库" }; block()
    }
    suspend fun <T> snapshot(block: suspend () -> T): T = mutex.withLock { block() }
    suspend fun reserve(checkDraft: suspend () -> Unit): Any = mutex.withLock {
        check(preview == null) { "已有待确认的恢复" }; checkDraft(); Any().also { preview = it }
    }
    suspend fun <T> restoring(token: Any, block: suspend () -> T): T = mutex.withLock {
        check(preview === token) { "恢复预览已失效，请重新选择备份" }; block()
    }
    suspend fun release(token: Any) = mutex.withLock { if (preview === token) preview = null }
}
