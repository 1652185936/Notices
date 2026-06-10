package com.yhx.notices.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 字号体系，见 docs/04-UI交互设计.md §1.1
val NoticesTypography = Typography(
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),   // H1
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),  // H2
    headlineSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),   // H3
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),                 // 正文
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),                // 辅助
    labelSmall = TextStyle(fontSize = 12.sp),                                    // 角标
)
