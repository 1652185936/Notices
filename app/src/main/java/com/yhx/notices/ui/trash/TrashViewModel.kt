package com.yhx.notices.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhx.notices.data.repository.NoteRepository
import com.yhx.notices.domain.model.NoteListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val noteRepo: NoteRepository,
) : ViewModel() {

    val items: StateFlow<List<NoteListItem>> = noteRepo.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(id: Long) = viewModelScope.launch { noteRepo.restore(listOf(id)) }
    fun purge(id: Long) = viewModelScope.launch { noteRepo.purge(listOf(id)) }
}
