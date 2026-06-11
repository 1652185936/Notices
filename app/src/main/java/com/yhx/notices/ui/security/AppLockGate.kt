package com.yhx.notices.ui.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity

/** 应用级锁：启动时要求生物识别/系统凭据后才显示内容。 */
@Composable
fun AppLockGate(onUnlock: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    fun auth() {
        activity?.let {
            BiometricAuth.authenticate(
                it, "应用已锁定", "请验证身份解锁",
                onSuccess = onUnlock, onError = {},
            )
        }
    }
    LaunchedEffect(Unit) { auth() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("应用已锁定", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { auth() }, modifier = Modifier.padding(top = 16.dp)) { Text("解锁") }
        }
    }
}
