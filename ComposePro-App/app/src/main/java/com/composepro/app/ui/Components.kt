package com.composepro.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/** Pill button. States: default, pressed (scale), disabled (40%), loading (spinner). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    small: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "press")
    val bg = if (primary) CP.Accent else CP.Raised
    val fg = if (primary) CP.OnDark else CP.Ink
    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .height(if (small) CPSpace.ChipHeight else CPSpace.ButtonHeight)
            .clip(CPShape.Pill)
            .background(bg)
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) CPSpace.S2 else CPSpace.S3),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Text(text, style = if (small) CPType.CaptionMedium else CPType.BodyMedium, color = fg, textAlign = TextAlign.Center)
    }
}

/** Plain text button with a 44dp tap area. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = CP.Ink) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = CPSpace.Tap)
            .clip(CPShape.Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CPSpace.S2),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = CPType.BodyMedium, color = color) }
}

/** Red error line with a small alert mark. Errors always come with words. */
@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(CPSpace.S1)) {
        Box(Modifier.padding(top = 1.dp).size(14.dp).clip(CPShape.Pill).background(CP.Off), contentAlignment = Alignment.Center) {
            Text("!", style = CPType.CaptionMedium, color = CP.OnDark)
        }
        Text(text, style = CPType.Caption, color = CP.Off)
    }
}
