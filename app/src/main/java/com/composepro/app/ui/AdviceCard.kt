package com.composepro.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composepro.app.ai.Move
import com.composepro.app.guide.P
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/** One photographer step as shown: done or not, whether the phone ticks it by itself, and the latest note. */
data class StepView(val move: Move, val done: Boolean, val live: Boolean, val note: String)

/** The pill above the shutter: "Ask photographer", then "Check my shot" once there's a plan. Spinner while waiting. */
@Composable
fun AskButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CPShape.Pill)
            .background(CP.Glass)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = CP.OnDark, strokeWidth = 2.dp)
        Text(label, style = CPType.CaptionMedium, color = CP.OnDark)
    }
}

/**
 * The photographer's plan as a checklist over the bottom of the camera view. Steps the phone can measure
 * (angle, where the subject sits) tick themselves; the rest tick after "Check my shot".
 */
@Composable
fun CoachCard(seen: String, steps: List<StepView>, next: String?, ready: Boolean, error: String?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val doneCount = steps.count { it.done }
    val allDone = ready || (steps.isNotEmpty() && doneCount == steps.size)
    Column(
        modifier.widthIn(max = 380.dp).fillMaxWidth().clip(CPShape.Card).background(CP.Glass.copy(alpha = 0.88f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (steps.isEmpty()) "PHOTOGRAPHER" else "PHOTOGRAPHER · $doneCount OF ${steps.size} DONE",
                style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.6f), modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(32.dp).clip(CPShape.Pill).clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "Close the photographer" },
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = CPType.BodyMedium, color = CP.OnDark) }
        }
        Text(seen, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.8f))
        steps.forEachIndexed { i, s -> StepRow(i, s) }
        val footer = when {
            allDone -> "✓ All done. Take the photo."
            next != null && next.isNotBlank() -> next
            steps.any { !it.done && !it.live } -> "Done these? Tap Check my shot."
            else -> null
        }
        footer?.let {
            Text(
                it, style = CPType.BodyStrong, color = if (allDone) CP.Right else CP.OnDark,
                modifier = Modifier.padding(top = CPSpace.S2),
            )
        }
        error?.let { Text(it, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.85f), modifier = Modifier.padding(top = CPSpace.S1)) }
    }
}

@Composable
private fun StepRow(index: Int, s: StepView) {
    Row(Modifier.padding(top = CPSpace.S1), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).clip(CPShape.Pill)
                .then(if (s.done) Modifier.background(CP.Right) else Modifier.border(2.dp, CP.OnDark.copy(alpha = 0.7f), CPShape.Pill)),
            contentAlignment = Alignment.Center,
        ) { Text(if (s.done) "✓" else "${index + 1}", style = CPType.Caption, color = CP.OnDark) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(s.move.action, style = CPType.BodyStrong, color = CP.OnDark.copy(alpha = if (s.done) 0.6f else 1f))
            val sub = when {
                s.done -> s.move.why
                s.note.isNotBlank() && !s.note.equals("Done", ignoreCase = true) -> s.note
                else -> s.move.why
            }
            Text(sub, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f))
            if (!s.done) {
                Text(
                    if (s.live) "Turns ✓ by itself when it's right" else "Tap Check my shot when done",
                    style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** When the first open step is about where the subject sits: a ring where it should go, a dot on it now, an arrow between. */
@Composable
fun CoachLayer(target: P?, subject: P?, hit: Boolean, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (target != null) 1f else 0f, tween(250), label = "coachAlpha")
    Canvas(modifier.alpha(a)) {
        val t = target ?: return@Canvas
        val w = size.width; val h = size.height
        val ring = 22f * density
        val to = Offset(t.x * w, t.y * h)
        if (hit) {
            drawCircle(CP.Right, ring, to)
            val s = 7f * density
            drawLine(CP.OnDark, to + Offset(-s, 0f), to + Offset(-s * 0.2f, s * 0.8f), 3f * density)
            drawLine(CP.OnDark, to + Offset(-s * 0.2f, s * 0.8f), to + Offset(s * 1.1f, -s * 0.8f), 3f * density)
            return@Canvas
        }
        drawCircle(CP.Glass, ring, to)
        drawCircle(CP.OnDark, ring, to, style = Stroke(3f * density))
        val p = subject ?: return@Canvas
        val from = Offset(p.x * w, p.y * h)
        val d = to - from
        val len = d.getDistance()
        drawCircle(CP.OnDark, 7f * density, from)
        if (len < ring * 1.5f) return@Canvas
        val u = d / len
        val end = to - u * (ring + 4f * density)
        drawLine(CP.OnDark, from + u * (9f * density), end, 3f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density, 7f * density)))
        val head = 12f * density
        val side = Offset(-u.y, u.x)
        val l = end - u * head + side * head * 0.6f
        val r = end - u * head - side * head * 0.6f
        drawPath(Path().apply { moveTo(end.x, end.y); lineTo(l.x, l.y); lineTo(r.x, r.y); close() }, CP.OnDark)
    }
}

/** A short failure card for the first ask (no plan yet): the message, Try again, close. */
@Composable
fun AskErrorCard(message: String?, onRetry: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 4 },
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().clip(CPShape.Card).background(CP.Glass.copy(alpha = 0.88f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(message.orEmpty(), style = CPType.Body, color = CP.OnDark)
            Row(Modifier.fillMaxWidth().padding(top = CPSpace.S2), horizontalArrangement = Arrangement.End) {
                CardButton("Try again", onRetry)
                CardButton("Close", onClose)
            }
        }
    }
}

@Composable
private fun CardButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = CPSpace.S1).clip(CPShape.Pill).background(CP.OnDark.copy(alpha = 0.15f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, style = CPType.CaptionMedium, color = CP.OnDark) }
}
