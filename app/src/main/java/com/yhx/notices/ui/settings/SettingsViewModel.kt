package com.yhx.notices.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.backup.BackupManager
import com.yhx.notices.data.repository.Settings
import com.yhx.notices.data.repository.ThemeMode
import com.yhx.notices.data.repository.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPrefs,
    private val backupManager: BackupManager,
) : ViewModel() {

    val settings: StateFlow<Settings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setTheme(mode) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            backupManager.export(uri)
                .onSuccess { _message.value = "已备份 $it 条笔记" }
                .onFailure { _message.value = "备份失败：${it.message}" }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            backupManager.import(uri)
                .onSuccess { _message.value = "已恢复 $it 条笔记" }
                .onFailure { _message.value = "恢复失败：${it.message}" }
        }
    }

    fun clearMessage() { _message.value = null }
}
