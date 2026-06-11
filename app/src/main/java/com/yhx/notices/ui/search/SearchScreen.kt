package com.yhx.notices.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 把文本中匹配查询词（不区分大小写、按空格分词）的片段高亮。 */
private fun highlight(text: String, query: String, color: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    val lower = text.lowercase()
    val ranges = ArrayList<IntRange>()
    for (term in terms) {
        val t = term.lowercase()
        var from = 0
        while (true) {
            val idx = lower.indexOf(t, from)
            if (idx < 0) break
            ranges.add(idx until (idx + t.length))
            from = idx + t.length
        }
    }
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        ranges.forEach { r ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = color,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
                r.first, r.last + 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("搜索笔记") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding)) {
            items(results, key = { it.id }) { note ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenNote(note.id) }
                        .padding(16.dp)
                ) {
                    Text(
                        highlight(note.title, query, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                    )
                    if (note.excerpt.isNotBlank()) {
                        Text(
                            highlight(note.excerpt, query, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
