package com.yhx.notices.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.repository.ThemeMode
import com.yhx.notices.data.repository.UserPrefs
import com.yhx.notices.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    prefs: UserPrefs,
    scheduler: ReminderScheduler,
) : ViewModel() {
    val theme: StateFlow<ThemeMode> = prefs.settings
        .map { it.theme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val appLock: StateFlow<Boolean> = prefs.settings
        .map { it.appLock }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var unlocked by mutableStateOf(false)
        private set

    fun markUnlocked() { unlocked = true }

    init {
        viewModelScope.launch { runCatching { scheduler.rescheduleActive() } }
    }
}
