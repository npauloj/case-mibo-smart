package io.github.npauloj.mibosmart.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * The type scale, tuned for the eight slots this app actually reads.
 *
 * Material's defaults are readable and characterless: every screen title arrives at the same weight as
 * a paragraph, and the numbers this app is full of — a token, a character counter, a countdown, a "há
 * 5 min" — are set in the same proportional face as prose, where digits have different widths and a
 * counter jitters as it counts.
 *
 * No font file is bundled. The hierarchy comes from weight, size and letter spacing, and [Tabular]
 * uses [FontFamily.Monospace], which every platform already has. A custom face would cost half a
 * megabyte in the APK to say something this app can say with weight.
 */
internal val AppTypography = Typography().run {
    copy(
        // Screen titles. Heavier and tighter than Material's default, so the first line of a screen
        // reads as a title at a glance rather than as a slightly larger sentence.
        headlineSmall = headlineSmall.copy(
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
        ),
        // Section headings inside a screen: the lock's "Estado", "Volume".
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        // The workhorse. A little more line height than Material's: these screens explain things —
        // what a command did, why a session ended — and explanations need room.
        bodyMedium = bodyMedium.copy(lineHeight = 22.sp),
        bodySmall = bodySmall.copy(lineHeight = 18.sp),
        // Button labels.
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        // Small captions and metadata: "última atualização há 3 h".
        labelMedium = labelMedium.copy(letterSpacing = 0.3.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.4.sp),
    )
}

/**
 * Data, not prose: the token mask, the `12/35` counter, a countdown, a model code, a timestamp.
 *
 * Monospace is not decoration here. A proportional face gives `1` and `8` different widths, so a
 * counter shifts sideways as the user pastes and a column of times fails to line up. It also draws a
 * line between what the app *says* and what the partner *returned* — the second kind is quoted, and
 * quoting it in a different face is the cheapest way to say so.
 */
internal val Tabular: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    // 13sp, not 14: a monospace glyph is wider than its proportional twin, and a 35-character token
    // mask at 14sp runs under the "Colar" button on a 360dp screen. Found in a golden.
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

/** [Tabular] at caption size, for metadata that sits under a line rather than in it. */
internal val TabularSmall: TextStyle = Tabular.copy(fontSize = 12.sp, lineHeight = 16.sp)

/** A counter is read right-aligned so its digits stay in the same place as it grows. */
internal val TabularCounter: TextStyle = TabularSmall.copy(textAlign = TextAlign.End)
