package com.seen.film.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LibraryWriteKind { PERSONAL, CATALOG }

/** Short write/snapshot gate; metadata downloads never hold this mutex. */
class LibraryAccess {
    private val mutex = Mutex()
    private var preview: Any? = null
    private var metadata: Any? = null
    suspend fun <T> write(kind: LibraryWriteKind = LibraryWriteKind.PERSONAL, block: suspend () -> T): T = mutex.withLock {
        check(preview == null) { "请先完成或取消备份恢复，再编辑影库" }
        check(kind != LibraryWriteKind.CATALOG || metadata == null) { "正在准备电影资料，请完成或取消后再增删影片" }
        block()
    }
    suspend fun <T> snapshot(block: suspend () -> T): T = mutex.withLock { block() }
    suspend fun reserve(checkDraft: suspend () -> Unit): Any = mutex.withLock {
        check(preview == null) { "已有待确认的恢复" }
        check(metadata == null) { "请先完成或取消电影资料更新，再恢复备份" }
        checkDraft(); Any().also { preview = it }
    }
    suspend fun <T> restoring(token: Any, block: suspend () -> T): T = mutex.withLock {
        check(preview === token) { "恢复预览已失效，请重新选择备份" }; block()
    }
    suspend fun release(token: Any) = mutex.withLock { if (preview === token) preview = null }
    suspend fun reserveMetadata(): Any = mutex.withLock {
        check(metadata == null && preview == null) { "请先完成或取消正在进行的数据操作" }
        Any().also { metadata = it }
    }
    suspend fun <T> publishMetadata(token: Any, block: suspend () -> T): T = mutex.withLock {
        check(metadata === token && preview == null) { "资料更新已失效，请重试" }; block()
    }
    suspend fun releaseMetadata(token: Any) = mutex.withLock { if (metadata === token) metadata = null }
}
