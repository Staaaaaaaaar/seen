package com.seen.film.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.seen.film.SeenApplication
import com.seen.film.data.MovieHistory
import com.seen.film.domain.ViewingDraft
import com.seen.film.domain.ViewingRules
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SeenViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as SeenApplication
    private val preferences = app.getSharedPreferences("appearance", 0)
    private val _library = MutableStateFlow<List<MovieHistory>?>(null)
    val library = _library.asStateFlow()
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed = _loadFailed.asStateFlow()
    private val _draft = MutableStateFlow<ViewingDraft?>(null)
    val draft = _draft.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _draftReady = MutableStateFlow(false)
    val draftReady = _draftReady.asStateFlow()
    private val _fieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val fieldErrors = _fieldErrors.asStateFlow()
    var timeView = preferences.getBoolean("timeView", false)
        private set
    private var baseline: ViewingDraft? = null
    private var pendingWrite: Job? = null
    val isDirty get() = _draft.value != baseline

    init {
        viewModelScope.launch {
            app.repository.library.catch { _loadFailed.value = true; _error.value = "无法读取电影库，请重启后重试" }.collect { _library.value = it }
        }
        viewModelScope.launch {
            try {
                val recovered = app.drafts.read()
                // A committed create whose draft cleanup was interrupted becomes
                // an edit of that stable ID, rather than an editable new movie.
                _draft.value = if (recovered != null && !recovered.isEditing &&
                    app.repository.viewing(recovered.key) != null) {
                    recovered.copy(isNewMovie = false, isEditing = true)
                } else recovered
                _draftReady.value = true
            } catch (e: Exception) {
                // Keep editing disabled rather than overwrite an unreadable draft.
                report(e, "无法读取未完成的编辑，新增暂不可用，请重新打开应用后重试")
            }
        }
    }
    private fun report(e: Exception, fallback: String) {
        if (e is CancellationException) throw e
        _error.value = when (e) {
            is IllegalArgumentException, is IllegalStateException -> e.message ?: fallback
            else -> fallback
        }
    }
    fun clearError() { _error.value = null }
    fun setTimeView(value: Boolean) { timeView = value; preferences.edit { putBoolean("timeView", value) } }

    fun openEditor(movieId: String? = null, viewingId: String? = null, navigate: (String) -> Unit) {
        if (_busy.value || !_draftReady.value) return
        _draft.value?.let {
            _error.value = "已恢复未完成的编辑，请先保存或放弃，再添加其他观看"
            navigate(it.key); return
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                val movie = movieId?.let { app.repository.movie(it) }
                check(movieId == null || movie != null) { "电影已不存在" }
                val viewing = viewingId?.let { app.repository.viewing(it) }
                check(viewingId == null || viewing != null) { "这次观看已被删除" }
                val value = if (movie == null) ViewingDraft() else ViewingDraft(
                    key = viewing?.id ?: java.util.UUID.randomUUID().toString(), movieId = movie.id,
                    isNewMovie = false, isEditing = viewing != null,
                    title = movie.title, originalTitle = movie.originalTitle.orEmpty(), year = movie.releaseYear?.toString().orEmpty(),
                    posterKey = movie.posterKey, date = viewing?.viewedDate ?: java.time.LocalDate.now().toString(),
                    time = viewing?.viewedTime.orEmpty(), location = viewing?.location.orEmpty(),
                    rating = viewing?.ratingHalfStars, notes = viewing?.notes.orEmpty(),
                )
                app.drafts.write(value)
                baseline = value; _draft.value = value; _fieldErrors.value = emptyMap()
                navigate(value.key)
            } catch (e: Exception) { report(e, "无法开始编辑") }
            finally { _busy.value = false }
        }
    }
    fun update(transform: (ViewingDraft) -> ViewingDraft) {
        if (_busy.value) return
        val current = _draft.value ?: return
        _draft.value = transform(current)
        _fieldErrors.value = emptyMap()
        persistDraft()
    }
    fun persistDraft() {
        if (_busy.value) return
        val value = _draft.value ?: return
        pendingWrite?.cancel()
        pendingWrite = viewModelScope.launch {
            delay(200)
            try { app.drafts.write(value) }
            catch (e: Exception) { report(e, "草稿暂存失败，请勿退出，先尝试保存") }
        }
    }
    fun save(onSaved: (String, String) -> Unit) {
        val value = _draft.value ?: return
        if (_busy.value) return
        _fieldErrors.value = ViewingRules.validate(value)
        if (_fieldErrors.value.isNotEmpty()) return
        _busy.value = true
        viewModelScope.launch {
            try {
                pendingWrite?.cancelAndJoin()
                app.drafts.write(value)
                val movieId = app.repository.save(value)
                // The record is committed even if draft cleanup fails. Its stable ID
                // also makes a recovered post-commit draft safe to save again.
                try { app.drafts.clear() }
                catch (e: Exception) { report(e, "观看已保存，但临时草稿清理失败") }
                _draft.value = null; baseline = null; _fieldErrors.value = emptyMap()
                onSaved(movieId, value.key)
            } catch (e: Exception) { report(e, "保存失败，输入内容仍保留，请重试") }
            finally { _busy.value = false }
        }
    }
    fun discard(onDone: () -> Unit) {
        if (_busy.value) return
        val value = _draft.value ?: return onDone()
        _busy.value = true
        viewModelScope.launch {
            try {
                pendingWrite?.cancelAndJoin()
                app.drafts.clear()
                _draft.value = null; baseline = null
                onDone()
                // File cleanup must not strand the editor after the draft is gone.
                if (value.isNewMovie) {
                    try { app.posters.removeUnused(value.posterKey, _library.value.orEmpty()) }
                    catch (e: Exception) { report(e, "编辑已放弃，临时海报稍后清理") }
                }
            } catch (e: Exception) { report(e, "无法放弃编辑，请重试") }
            finally { _busy.value = false }
        }
    }
    fun selectPoster(uri: Uri) {
        val current = _draft.value ?: return
        if (_busy.value || !current.isNewMovie) return
        _busy.value = true
        viewModelScope.launch {
            try {
                pendingWrite?.cancelAndJoin()
                val key = app.posters.import(uri)
                val updated = current.copy(posterKey = key)
                _draft.value = updated
                app.drafts.write(updated)
                app.posters.removeUnused(current.posterKey, _library.value.orEmpty())
            } catch (e: Exception) { report(e, "无法保存海报，请选择其他图片") }
            finally { _busy.value = false }
        }
    }
    fun deleteViewing(id: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { app.repository.deleteViewing(id) }
            catch (e: Exception) { report(e, "删除失败，请重试") }
            finally { _busy.value = false }
        }
    }
}
