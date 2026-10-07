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
import android.graphics.RectF
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import com.composepro.app.ai.CheckBy
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
fun CoachCard(
    frame: String?, frameWhy: String, steps: List<StepView>, ready: Boolean, checking: Boolean, error: String?,
    onClose: () -> Unit, onTick: (Int) -> Unit, onNewSteps: () -> Unit, newScene: Boolean = false, modifier: Modifier = Modifier,
) {
    // All steps at once under the camera view (user request, 2026-10-06). The one to do now is bold and carries its
    // hint; the camera view only shows the ring/box for that one.
    val allDone = ready || (steps.isNotEmpty() && steps.all { it.done })
    val current = steps.indexOfFirst { !it.done }
    Column(
        modifier.widthIn(max = 420.dp).fillMaxWidth().clip(CPShape.Card).background(CP.Glass)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Compose Pro" + (frame?.let { " · $it" } ?: "") + " · ${steps.count { it.done }} of ${steps.size} done",
                style = CPType.CaptionMedium, color = CP.OnDark.copy(alpha = 0.75f), modifier = Modifier.weight(1f),
            )
            if (checking) CircularProgressIndicator(Modifier.size(12.dp), color = CP.OnDark.copy(alpha = 0.7f), strokeWidth = 1.5.dp)
            // Fresh steps for whatever the camera sees now: the person decides when it's a new photo.
            Box(
                Modifier.size(36.dp).clip(CPShape.Pill).clickable(role = Role.Button, onClick = onNewSteps)
                    .semantics { contentDescription = "New steps for what's in view now" },
                contentAlignment = Alignment.Center,
            ) { Text("↻", style = CPType.BodyStrong, color = CP.OnDark) }
            Box(
                Modifier.size(36.dp).clip(CPShape.Pill).clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "Close the steps" },
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = CPType.BodyMedium, color = CP.OnDark) }
        }
        steps.forEachIndexed { i, s ->
            val now = i == current
            Row(
                Modifier.padding(top = if (i == 0) 0.dp else 8.dp).clip(CPShape.Thumb)
                    .clickable(role = Role.Checkbox, onClickLabel = if (s.done) "Untick" else "Tick off") { onTick(i) },
                verticalAlignment = Alignment.Top,
            ) {
                // Red number = still to do; green ✓ = done.
                Box(
                    Modifier.padding(top = 1.dp).size(22.dp).clip(CPShape.Pill).background(if (s.done) CP.Right else CP.Off),
                    contentAlignment = Alignment.Center,
                ) { Text(if (s.done) "✓" else "${i + 1}", style = CPType.CaptionMedium, color = CP.OnDark) }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        s.move.action, maxLines = 2,
                        style = if (now) CPType.BodyStrong else CPType.Body,
                        color = CP.OnDark.copy(alpha = if (s.done) 0.55f else 1f),
                    )
                    if (now) {
                        val hint = when {
                            s.move.check == CheckBy.Frame && s.move.size != null -> "Move until the outline fills the white box."
                            s.move.check == CheckBy.Frame -> "Move the phone until the dot is inside the circle."
                            s.move.check == CheckBy.Zoom -> "Tap ${s.move.zoom?.let { if (it < 1f) String.format(java.util.Locale.US, "%.1f", it) else it.toInt().toString() }}× at the bottom-right of the camera view."
                            s.note.isNotBlank() && !s.note.equals("Done", ignoreCase = true) -> s.note
                            else -> s.move.why
                        }
                        Text(hint, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.8f), maxLines = 2)
                    }
                }
            }
        }
        if (newScene) {
            Text(
                "Pointing at something new? Tap ↻ for fresh steps.", style = CPType.CaptionMedium, color = CP.OnDark,
                modifier = Modifier.padding(top = 8.dp).clickable(role = Role.Button, onClick = onNewSteps),
            )
        }
        if (!allDone && steps.size > 1) {
            Text("Can't do a step? Tap it to tick it off.", style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.55f), modifier = Modifier.padding(top = 8.dp))
        }
        if (allDone) {
            Text("✓ All done. Take the photo.", style = CPType.BodyStrong, color = CP.Right, modifier = Modifier.padding(top = 8.dp))
            if (frameWhy.isNotBlank()) Text("${frame ?: "Frame"}: $frameWhy", style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f))
        }
        error?.let { Text(it, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** The same card while the steps are being worked out, so there's only ever one thing to read. */
@Composable
fun FindingCard(modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(max = 420.dp).fillMaxWidth().clip(CPShape.Card).background(CP.Glass)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = CP.OnDark, strokeWidth = 2.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text("Finding the best shot…", style = CPType.BodyStrong, color = CP.OnDark)
            Text("Hold the phone still for a second.", style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f))
        }
    }
}

/** When the first open step is about where the subject sits: a ring where it should go, a dot on it now, an arrow between. */
@Composable
fun CoachLayer(target: P?, subject: P?, subjectBox: RectF?, targetSize: Float?, hit: Boolean, modifier: Modifier = Modifier) {
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
        // A faint outline around what the dot is on, so it's clear which thing has to move.
        subjectBox?.let { b ->
            drawRoundRect(
                CP.OnDark.copy(alpha = 0.6f), Offset(b.left * w, b.top * h), Size(b.width() * w, b.height() * h),
                CornerRadius(14f * density), style = Stroke(2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * density, 6f * density))),
            )
        }
        // When the step is about size too: a white box at the spot, as big as the subject should be
        // (its height from the photographer, its width keeping the subject's own shape).
        if (targetSize != null) {
            val bh = targetSize * h
            val aspect = subjectBox?.let { (it.width() * w) / (it.height() * h).coerceAtLeast(1f) } ?: 1f
            val bw = (bh * aspect).coerceAtMost(w * 0.98f)
            drawRoundRect(
                CP.OnDark, Offset(to.x - bw / 2, to.y - bh / 2), Size(bw, bh), CornerRadius(16f * density),
                style = Stroke(3f * density),
            )
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
