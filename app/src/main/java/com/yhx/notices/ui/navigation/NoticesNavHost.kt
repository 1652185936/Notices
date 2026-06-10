package com.yhx.notices.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yhx.notices.ui.editor.NoteEditorScreen
import com.yhx.notices.ui.notes.NotesListScreen
import com.yhx.notices.ui.search.SearchScreen
import com.yhx.notices.ui.settings.SettingsScreen
import com.yhx.notices.ui.todo.TodoScreen

object Routes {
    const val NOTES = "notes"
    const val TODO = "todo"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val EDITOR = "editor/{noteId}"
    fun editor(noteId: Long) = "editor/$noteId"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.NOTES, "笔记", Icons.AutoMirrored.Outlined.EventNote),
    BottomTab(Routes.TODO, "待办", Icons.Outlined.CheckCircle),
    BottomTab(Routes.SETTINGS, "我的", Icons.Outlined.Person),
)

@Composable
fun NoticesNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomTabs.map { it.route }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.NOTES,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.NOTES) {
                NotesListScreen(
                    onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.TODO) { TodoScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) {
                NoteEditorScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
