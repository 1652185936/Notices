package com.yhx.notices.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yhx.notices.domain.model.ListLayout
import com.yhx.notices.domain.model.NoteSort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val layout: ListLayout = ListLayout.GRID,
    val sort: NoteSort = NoteSort.UPDATED,
    val trashRetentionDays: Int = 30,
    val appLock: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class UserPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val THEME = stringPreferencesKey("theme")
    private val LAYOUT = stringPreferencesKey("layout")
    private val SORT = stringPreferencesKey("sort")
    private val TRASH_DAYS = intPreferencesKey("trash_days")
    private val APP_LOCK = booleanPreferencesKey("app_lock")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            layout = p[LAYOUT]?.let { runCatching { ListLayout.valueOf(it) }.getOrNull() } ?: ListLayout.GRID,
            sort = p[SORT]?.let { runCatching { NoteSort.valueOf(it) }.getOrNull() } ?: NoteSort.UPDATED,
            trashRetentionDays = p[TRASH_DAYS] ?: 30,
            appLock = p[APP_LOCK] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[THEME] = mode.name }
    suspend fun setLayout(layout: ListLayout) = context.dataStore.edit { it[LAYOUT] = layout.name }
    suspend fun setSort(sort: NoteSort) = context.dataStore.edit { it[SORT] = sort.name }
    suspend fun setTrashDays(days: Int) = context.dataStore.edit { it[TRASH_DAYS] = days }
    suspend fun setAppLock(enabled: Boolean) = context.dataStore.edit { it[APP_LOCK] = enabled }
}
