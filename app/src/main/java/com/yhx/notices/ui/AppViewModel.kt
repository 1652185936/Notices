package com.yhx.notices.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.repository.ThemeMode
import com.yhx.notices.data.repository.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(prefs: UserPrefs) : ViewModel() {
    val theme: StateFlow<ThemeMode> = prefs.settings
        .map { it.theme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
}
