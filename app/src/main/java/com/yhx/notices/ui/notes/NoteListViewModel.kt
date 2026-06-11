package com.yhx.notices.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.local.dao.FolderWithCount
import com.yhx.notices.data.repository.FolderRepository
import com.yhx.notices.data.repository.NoteRepository
import com.yhx.notices.data.repository.UserPrefs
import com.yhx.notices.domain.model.ListLayout
import com.yhx.notices.domain.model.NoteListItem
import com.yhx.notices.domain.model.NoteSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteListUiState(
    val notes: List<NoteListItem> = emptyList(),
    val folders: List<FolderWithCount> = emptyList(),
    val currentFolderId: Long? = null,
    val sort: NoteSort = NoteSort.UPDATED,
    val layout: ListLayout = ListLayout.GRID,
    val selection: Set<Long> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val noteRepo: NoteRepository,
    private val folderRepo: FolderRepository,
    private val prefs: UserPrefs,
) : ViewModel() {

    private val currentFolderId = MutableStateFlow<Long?>(null)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    private val notesFlow = combine(currentFolderId, prefs.settings) { folderId, settings ->
        folderId to settings.sort
    }.flatMapLatest { (folderId, sort) ->
        noteRepo.observeNotes(folderId, sort)
    }

    val uiState: StateFlow<NoteListUiState> = combine(
        notesFlow,
        folderRepo.observeFolders(),
        currentFolderId,
        selection,
        prefs.settings,
    ) { notes, folders, folderId, sel, settings ->
        NoteListUiState(
            notes = notes,
            folders = folders,
            currentFolderId = folderId,
            sort = settings.sort,
            layout = settings.layout,
            selection = sel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoteListUiState())

    fun selectFolder(id: Long?) { currentFolderId.value = id }

    fun toggleSelect(id: Long) {
        selection.value = if (id in selection.value) selection.value - id else selection.value + id
    }

    fun clearSelection() { selection.value = emptySet() }

    suspend fun createNote(): Long = noteRepo.createNote(currentFolderId.value)

    suspend fun createCanvasNote(): Long = noteRepo.createNote(currentFolderId.value, isCanvas = true)

    fun deleteSelected() {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch { noteRepo.moveToTrash(ids); clearSelection() }
    }

    fun pinSelected() {
        val ids = selection.value.toList()
        viewModelScope.launch { noteRepo.setPinned(ids, true); clearSelection() }
    }

    fun toggleLayout() {
        viewModelScope.launch {
            val next = if (uiState.value.layout == ListLayout.GRID) ListLayout.LIST else ListLayout.GRID
            prefs.setLayout(next)
        }
    }

    fun setSort(sort: NoteSort) {
        viewModelScope.launch { prefs.setSort(sort) }
    }

    fun createFolder(name: String) {
        viewModelScope.launch { folderRepo.create(name, 0xFF007DFF.toInt()) }
    }
}
