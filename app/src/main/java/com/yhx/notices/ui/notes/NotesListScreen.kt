package com.yhx.notices.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yhx.notices.domain.model.NoteListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotesListScreen(
    onOpenNote: (Long) -> Unit,
    onSearch: () -> Unit = {},
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val inSelection = state.selection.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (inSelection) "已选 ${state.selection.size} 项"
                        else folderName(state)
                    )
                },
                actions = {
                    if (inSelection) {
                        IconButton(onClick = viewModel::pinSelected) {
                            Icon(Icons.Default.PushPin, contentDescription = "置顶")
                        }
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    } else {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!inSelection) {
                FloatingActionButton(onClick = {
                    scope.launch { onOpenNote(viewModel.createNote()) }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "新建笔记")
                }
            }
        },
    ) { innerPadding ->
        if (state.notes.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("点击 + 新建第一篇笔记", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        selected = note.id in state.selection,
                        selectionMode = inSelection,
                        onClick = {
                            if (inSelection) viewModel.toggleSelect(note.id) else onOpenNote(note.id)
                        },
                        onLongClick = { viewModel.toggleSelect(note.id) },
                    )
                }
            }
        }
    }
}

private fun folderName(state: NoteListUiState): String =
    state.folders.firstOrNull { it.folder.id == state.currentFolderId }?.folder?.name ?: "全部笔记"

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteListItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (note.isFavorite) {
                    Icon(
                        Icons.Outlined.Star, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            if (note.firstImagePath != null) {
                AsyncImage(
                    model = java.io.File(
                        androidx.compose.ui.platform.LocalContext.current.filesDir,
                        note.firstImagePath
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x11000000)),
                )
            }
            if (note.isEncrypted) {
                Text("🔒 已加密", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            } else if (note.excerpt.isNotBlank()) {
                Text(
                    text = note.excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (note.isPinned) {
                Icon(
                    Icons.Default.PushPin, contentDescription = "置顶",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp).height(16.dp),
                )
            }
        }
    }
}
