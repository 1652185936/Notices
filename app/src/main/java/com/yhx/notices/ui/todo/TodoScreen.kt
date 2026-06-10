package com.yhx.notices.ui.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhx.notices.data.local.entity.TodoEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("待办") }) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("添加待办…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.add(input); input = "" }) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                group("已逾期", groups.overdue, viewModel)
                group("今天", groups.today, viewModel)
                group("未来", groups.upcoming, viewModel)
                group("无日期", groups.noDate, viewModel)
                group("已完成", groups.done, viewModel)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    title: String, entries: List<TodoEntity>, vm: TodoViewModel,
) {
    if (entries.isEmpty()) return
    item(key = "header_$title") {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
    items(entries, key = { it.id }) { todo -> TodoRow(todo, vm) }
}

@Composable
private fun TodoRow(todo: TodoEntity, vm: TodoViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = todo.isDone, onCheckedChange = { vm.toggle(todo) })
        Column(Modifier.weight(1f)) {
            Text(
                todo.content,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                color = if (todo.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground,
            )
            todo.dueAt?.let {
                Text(
                    "⏰ " + SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!todo.isDone) {
            IconButton(onClick = { pickDateTime(context) { vm.setReminder(todo, it) } }) {
                Icon(Icons.Default.Alarm, contentDescription = "设提醒")
            }
        }
    }
}

private fun pickDateTime(context: android.content.Context, onPicked: (Long) -> Unit) {
    val now = java.util.Calendar.getInstance()
    android.app.DatePickerDialog(
        context,
        { _, year, month, day ->
            android.app.TimePickerDialog(
                context,
                { _, hour, minute ->
                    val cal = java.util.Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    onPicked(cal.timeInMillis)
                },
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
                true,
            ).show()
        },
        now.get(java.util.Calendar.YEAR),
        now.get(java.util.Calendar.MONTH),
        now.get(java.util.Calendar.DAY_OF_MONTH),
    ).show()
}
