package com.yhx.notices.ui.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** 生物识别 + 系统密码兜底的解锁门禁（见 docs/06 §1.2）。 */
object BiometricAuth {

    private const val STRONG = BiometricManager.Authenticators.BIOMETRIC_STRONG
    private const val WEAK = BiometricManager.Authenticators.BIOMETRIC_WEAK
    private const val CREDENTIAL = BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    onError(msg.toString())
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(STRONG or CREDENTIAL)
        } else {
            builder.setAllowedAuthenticators(WEAK)
            builder.setNegativeButtonText("取消")
        }
        runCatching { prompt.authenticate(builder.build()) }
            .onFailure { onError(it.message ?: "无法启动验证") }
    }
}
