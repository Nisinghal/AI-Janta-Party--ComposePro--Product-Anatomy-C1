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

/**
 * The photographer's plan as a checklist over the bottom of the camera view. Open steps have red numbers and
 * turn into a green ✓ by themselves: steps the phone can measure (angle, where the subject sits) live, the rest
 * when the photographer looks again after the person changes something (user decision, 2026-10-06).
 */
@Composable
fun CoachCard(seen: String, steps: List<StepView>, next: String?, ready: Boolean, checking: Boolean, error: String?, onClose: () -> Unit, modifier: Modifier = Modifier) {
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
            if (checking) {
                CircularProgressIndicator(Modifier.size(12.dp), color = CP.OnDark.copy(alpha = 0.7f), strokeWidth = 1.5.dp)
                Text("Checking", style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.7f), modifier = Modifier.padding(start = 6.dp, end = 4.dp))
            }
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
        // Red number = still to do; green ✓ = done.
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).clip(CPShape.Pill).background(if (s.done) CP.Right else CP.Off),
            contentAlignment = Alignment.Center,
        ) { Text(if (s.done) "✓" else "${index + 1}", style = CPType.CaptionMedium, color = CP.OnDark) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(s.move.action, style = CPType.BodyStrong, color = CP.OnDark.copy(alpha = if (s.done) 0.6f else 1f))
            val sub = when {
                s.done -> s.move.why
                s.note.isNotBlank() && !s.note.equals("Done", ignoreCase = true) -> s.note
                else -> s.move.why
            }
            Text(sub, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f))
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
