package com.composepro.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composepro.app.ai.AskResult
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/** The "Ask photographer" pill under the camera. Shows a spinner while it waits. */
@Composable
fun AskButton(asking: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CPShape.Pill)
            .background(CP.Glass)
            .clickable(enabled = enabled && !asking, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (asking) CircularProgressIndicator(Modifier.size(14.dp), color = CP.OnDark, strokeWidth = 2.dp)
        Text(if (asking) "Looking like a photographer…" else "Ask photographer", style = CPType.CaptionMedium, color = CP.OnDark)
    }
}

/**
 * The photographer's answer over the bottom of the camera view: what it sees, then 2–3 numbered moves
 * with reasons. Errors show in the same card with a "Try again".
 */
@Composable
fun AdviceCard(result: AskResult?, asking: Boolean, onClose: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = result != null && !asking,
        enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 4 },
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().clip(CPShape.Card).background(CP.Glass.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            when (result) {
                is AskResult.Ok -> {
                    val a = result.advice
                    Text("PHOTOGRAPHER", style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.6f))
                    Text(a.seen, style = CPType.Body, color = CP.OnDark, modifier = Modifier.padding(top = 4.dp))
                    if (a.ready || a.moves.isEmpty()) {
                        Text("✓ This looks good. Take it.", style = CPType.BodyStrong, color = CP.Right, modifier = Modifier.padding(top = CPSpace.S1))
                    } else {
                        a.moves.take(3).forEachIndexed { i, m ->
                            Row(Modifier.padding(top = CPSpace.S1)) {
                                Text("${i + 1}", style = CPType.BodyStrong, color = CP.OnDark, modifier = Modifier.width(22.dp))
                                Column {
                                    Text(m.action, style = CPType.BodyStrong, color = CP.OnDark)
                                    if (m.why.isNotBlank()) Text(m.why, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f))
                                }
                            }
                        }
                    }
                    CardButtons(onClose, null)
                }
                is AskResult.Failed -> {
                    Text(result.message, style = CPType.Body, color = CP.OnDark)
                    CardButtons(onClose, onRetry)
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun CardButtons(onClose: () -> Unit, onRetry: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = CPSpace.S2), horizontalArrangement = Arrangement.End) {
        if (onRetry != null) CardButton("Try again", onRetry)
        CardButton("Got it", onClose)
    }
}

@Composable
private fun CardButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = CPSpace.S1).clip(CPShape.Pill).background(CP.OnDark.copy(alpha = 0.15f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, style = CPType.CaptionMedium, color = CP.OnDark) }
}
