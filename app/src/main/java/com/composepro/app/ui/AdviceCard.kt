package com.composepro.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
        modifier.widthIn(max = 420.dp).fillMaxWidth().clip(CPShape.Card).background(CP.Glass.copy(alpha = 0.9f))
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
        modifier.widthIn(max = 420.dp).fillMaxWidth().clip(CPShape.Card).background(CP.Glass.copy(alpha = 0.9f))
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

/**
 * Which way to move the phone, read from the step's words ("Move the phone slightly to the right" → right).
 * Used when the subject isn't being followed, so there's still an arrow to go by.
 */
fun phoneDirection(action: String): Offset? {
    val t = action.lowercase()
    val x = when {
        Regex("""\bleft\b""").containsMatchIn(t) -> -1f
        Regex("""\bright\b(?!\s+(in|at|there|on|now|away))""").containsMatchIn(t) -> 1f
        else -> 0f
    }
    val y = when {
        Regex("""\b(up|higher|raise)\b""").containsMatchIn(t) -> -1f
        Regex("""\b(down|lower)\b""").containsMatchIn(t) -> 1f
        else -> 0f
    }
    if (x == 0f && y == 0f) return null
    val len = kotlin.math.sqrt(x * x + y * y)
    return Offset(x / len, y / len)
}

/**
 * When the first open step is about where the subject sits: a ring where it should go, a dot on it now, and moving
 * chevrons from the ring showing which way to MOVE THE PHONE (user test 2026-10-08: "this arrow guide is not working").
 * The old arrow ran from the dot to the ring, the way the subject has to travel on screen, which is the opposite of
 * the way the phone moves, so "move the phone right" came with an arrow pointing left. When the subject isn't being
 * followed (no dot), the chevrons come from the step's own words ([phoneDir]).
 */
@Composable
fun CoachLayer(target: P?, subject: P?, subjectBox: RectF?, targetSize: Float?, hit: Boolean, phoneDir: Offset? = null, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (target != null) 1f else 0f, tween(250), label = "coachAlpha")
    val flow by rememberInfiniteTransition(label = "chevrons").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "flow",
    )
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

        // Which way the phone should go: towards the subject (the subject then slides into the ring).
        val dir: Offset? = subject?.let { p ->
            val from = Offset(p.x * w, p.y * h)
            val d = from - to
            val len = d.getDistance()
            drawCircle(CP.OnDark, 7f * density, from)
            if (len < ring * 1.5f) null else {
                // A faint dashed line joins the dot and the ring; the chevrons carry the direction.
                drawLine(
                    CP.OnDark.copy(alpha = 0.5f), to + d / len * (ring + 4f * density), from - d / len * (9f * density), 2f * density,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 8f * density)),
                )
                d / len
            }
        } ?: phoneDir
        if (dir != null) chevrons(to, dir, ring, flow)
    }
}

/** Three chevrons flowing out of the ring in [dir]: "move the phone this way". */
private fun DrawScope.chevrons(at: Offset, dir: Offset, ring: Float, flow: Float) {
    val side = Offset(-dir.y, dir.x)
    val s = 11f * density
    val gap = 20f * density
    for (k in 0 until 3) {
        val f = (k + flow) / 3f                       // 0..1 along the run
        val c = at + dir * (ring + 14f * density + f * gap * 3f)
        val alpha = (1f - f) * 0.95f + 0.05f
        val tip = c + dir * (s * 0.6f)
        val l = c - dir * (s * 0.6f) + side * s
        val r = c - dir * (s * 0.6f) - side * s
        val path = Path().apply { moveTo(l.x, l.y); lineTo(tip.x, tip.y); lineTo(r.x, r.y) }
        drawPath(path, Color.Black.copy(alpha = 0.35f * alpha), style = Stroke(7f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, CP.OnDark.copy(alpha = alpha), style = Stroke(4f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
