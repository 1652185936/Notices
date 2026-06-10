package com.yhx.notices

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yhx.notices.ui.AppViewModel
import com.yhx.notices.ui.navigation.NoticesNavHost
import com.yhx.notices.ui.theme.NoticesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            NoticesTheme(themeMode = theme) {
                NoticesNavHost()
            }
        }
    }
}
