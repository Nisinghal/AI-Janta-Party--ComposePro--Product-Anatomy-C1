package com.composepro.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Spacing and shape from DESIGN_LANGUAGE.md.
object CPSpace {
    val S1 = 8.dp
    val S2 = 12.dp
    val S3 = 20.dp
    val S4 = 32.dp
    val ButtonHeight = 48.dp
    val ChipHeight = 32.dp
    val Tap = 44.dp
}

object CPShape {
    val Sheet = RoundedCornerShape(22.dp)
    val Card = RoundedCornerShape(14.dp)
    val Input = RoundedCornerShape(12.dp)
    val Thumb = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(50)
}

// The design commits to one light look (Pose AI direction), so there is no dark scheme.
private val Scheme = lightColorScheme(
    primary = CP.Accent,
    onPrimary = CP.OnDark,
    background = CP.Surface,
    onBackground = CP.Ink,
    surface = CP.Surface,
    onSurface = CP.Ink,
    surfaceVariant = CP.Raised,
    onSurfaceVariant = CP.Muted,
    error = CP.Off,
)

@Composable
fun ComposeProTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography, content = content)
}
