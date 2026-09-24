package io.github.npauloj.mibosmart.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** The type scale, tuned for the eight slots this app actually reads. */
internal val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
        ),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = bodyMedium.copy(lineHeight = 22.sp),
        bodySmall = bodySmall.copy(lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(letterSpacing = 0.3.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.4.sp),
    )
}

/**
 * Data, not prose: the token mask, the `12/35` counter, a countdown, a model code, a timestamp.
 */
internal val Tabular: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

/** [Tabular] at caption size, for metadata that sits under a line rather than in it. */
internal val TabularSmall: TextStyle = Tabular.copy(fontSize = 12.sp, lineHeight = 16.sp)

/** A counter is read right-aligned so its digits stay in the same place as it grows. */
internal val TabularCounter: TextStyle = TabularSmall.copy(textAlign = TextAlign.End)
