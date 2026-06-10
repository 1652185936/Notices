package com.yhx.notices.ui

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

    init {
        // 启动时重建未来提醒（弥补进程被杀/系统重启），见 docs/06 §3
        viewModelScope.launch { runCatching { scheduler.rescheduleActive() } }
    }
}
