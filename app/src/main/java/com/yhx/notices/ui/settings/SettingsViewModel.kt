package com.yhx.notices.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.repository.Settings
import com.yhx.notices.data.repository.ThemeMode
import com.yhx.notices.data.repository.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPrefs,
) : ViewModel() {

    val settings: StateFlow<Settings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setTheme(mode) }
    }
}
