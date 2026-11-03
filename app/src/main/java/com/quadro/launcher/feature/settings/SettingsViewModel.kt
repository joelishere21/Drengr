package com.quadro.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadro.launcher.core.datastore.SettingsRepository
import com.quadro.launcher.core.designsystem.AccentColor
import com.quadro.launcher.core.domain.AppLayout
import com.quadro.launcher.core.domain.PageId
import com.quadro.launcher.core.domain.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val _isDefaultHome = MutableStateFlow(false)
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages

    fun refreshDefaultHome() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        _isDefaultHome.value = resolved?.activityInfo?.packageName == context.packageName
    }

    fun setAccent(accent: AccentColor) = launch { repository.setAccent(accent) }
    fun setLayout(layout: AppLayout) = launch { repository.setAppLayout(layout) }
    fun setColumns(columns: Int) = launch { repository.setGridColumns(columns) }
    fun setUse24Hour(value: Boolean?) = launch { repository.setUse24Hour(value) }
    fun setSwipeDown(enabled: Boolean) = launch { repository.setSwipeDownForCommandCenter(enabled) }
    fun movePage(page: PageId, delta: Int) = launch { repository.movePage(page, delta) }
    fun setPageHidden(page: PageId, hidden: Boolean) = launch { repository.setPageHidden(page, hidden) }

    fun export(uri: Uri) = launch {
        runCatching {
            val json = repository.exportJson()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }
        }.onSuccess { _messages.emit("Settings exported") }
            .onFailure { _messages.emit("Export failed: ${it.message}") }
    }

    fun import(uri: Uri) = launch {
        runCatching {
            val json = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            }
            repository.importJson(json)
        }.onSuccess { _messages.emit("Settings imported") }
            .onFailure { _messages.emit("Import failed: ${it.message}") }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
