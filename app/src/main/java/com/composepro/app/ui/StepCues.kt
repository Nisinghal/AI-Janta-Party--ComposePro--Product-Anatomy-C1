package com.composepro.app.ui

import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.composepro.app.ai.CheckBy
import com.composepro.app.ai.angleHint
import com.composepro.app.camera.Tilt
import com.composepro.app.guide.P
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPType
import kotlin.math.abs

/**
 * What the camera view shows for the step to do now, so it's seen and not only read (user request 2026-10-09:
 * "make it more visual: show them an arrow for the direction, or a box to bring the object here").
 */
sealed interface Cue {
    /** Move yourself or the phone that way: a big arrow at that edge of the view. */
    data class Edge(val dir: Offset, val label: String) : Cue
    /** Step closer (brackets grow) or back (brackets shrink). */
    data class Distance(val closer: Boolean) : Cue
    /** Point the camera more down (↓) or more forward (↑). */
    data class Aim(val down: Boolean) : Cue
    /** Tap a zoom button: a dashed box shows what that zoom will show. */
    data class Zoom(val level: Float) : Cue
    /** Move a thing on the table that way: an arrow from the thing itself. */
    data class Thing(val dir: Offset, val label: String) : Cue
}

private val LEFT = Offset(-1f, 0f)
private val RIGHT = Offset(1f, 0f)
private val UP = Offset(0f, -1f)
private val DOWN = Offset(0f, 1f)

/** The cue for [step]: from what the phone measures when it can (tilt, where the subject is), else from its words. */
fun cueFor(step: StepView, tilt: Tilt, subject: P?): Cue? {
    val m = step.move
    val t = m.action.lowercase()
    fun has(re: String) = Regex(re).containsMatchIn(t)
    return when (m.check) {
        CheckBy.Zoom -> Cue.Zoom(m.zoom ?: 2f)
        CheckBy.Angle -> when (angleHint(m.angle, tilt)?.takeIf { step.live }) {
            null -> textCue(t, ::has)
            else -> when {
                angleHint(m.angle, tilt)!!.contains("down") -> Cue.Aim(down = true)
                angleHint(m.angle, tilt)!!.contains("forward") -> Cue.Aim(down = false)
                else -> null   // already right: nothing to point at
            }
        }
        CheckBy.Frame -> {
            val tx = m.targetX; val ty = m.targetY
            if (subject != null && tx != null && ty != null) {
                // The phone goes towards the subject; the subject then slides into the box. One clear direction.
                val dx = subject.x - tx; val dy = subject.y - ty
                when {
                    abs(dx) < 0.05f && abs(dy) < 0.05f -> null
                    abs(dx) >= abs(dy) -> if (dx > 0) Cue.Edge(RIGHT, "Step right") else Cue.Edge(LEFT, "Step left")
                    else -> if (dy < 0) Cue.Edge(UP, "Aim a little higher") else Cue.Edge(DOWN, "Aim a little lower")
                }
            } else textCue(t, ::has)
        }
        CheckBy.Other -> textCue(t, ::has)
    }
}

private fun textCue(t: String, has: (String) -> Boolean): Cue? {
    val left = has("""\bleft\b"""); val right = has("""\bright\b(?!\s+(in|at|there|on|now|away))""")
    // Moving a thing on the table ("Move the cup to the right", "Slide the plate closer to you").
    if (has("""^(move|slide|push|pull|put|shift) (the|your) (?!phone|camera)""")) {
        val dir = when {
            left -> LEFT
            right -> RIGHT
            has("""closer to you|towards? you|forward""") -> DOWN
            has("""further back|farther back|\bback\b|\baway\b""") -> UP
            else -> null
        } ?: return null
        return Cue.Thing(dir, "Move it this way")
    }
    return when {
        has("""\b(step|move|get|come) closer\b""") -> Cue.Distance(closer = true)
        has("""\bstep back\b|\bmove back\b|\bback up\b""") -> Cue.Distance(closer = false)
        has("""point the camera more down|camera down""") -> Cue.Aim(down = true)
        has("""point the camera more forward|camera forward|more forward""") -> Cue.Aim(down = false)
        left -> Cue.Edge(LEFT, "Step left")
        right -> Cue.Edge(RIGHT, "Step right")
        has("""\b(higher|raise)\b""") -> Cue.Edge(UP, "Hold the phone higher")
        has("""\b(lower|crouch)\b""") -> Cue.Edge(DOWN, "Hold the phone lower")
        else -> null
    }
}

/** Draws [cue] over the camera view, with a short label beside it. "Bring it here" sits over a frame step's box. */
@Composable
fun StepCues(cue: Cue?, target: P?, targetHalfHeight: Float?, subjectBox: RectF?, modifier: Modifier = Modifier) {
    val bob by rememberInfiniteTransition(label = "cue").animateFloat(
        0f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val a by animateFloatAsState(if (cue != null || target != null) 1f else 0f, tween(250), label = "cueAlpha")
    BoxWithConstraints(modifier.alpha(a)) {
        val labels = mutableListOf<Triple<String, Float, Float>>()   // text, x, y (fractions of the view)
        target?.let { p -> labels += Triple("Bring it here", p.x, (p.y - (targetHalfHeight ?: 0.11f) - 0.035f).coerceAtLeast(0.06f)) }
        when (cue) {
            is Cue.Edge -> labels += when (cue.dir) {
                RIGHT -> Triple(cue.label, 0.78f, 0.50f)
                LEFT -> Triple(cue.label, 0.22f, 0.50f)
                UP -> Triple(cue.label, 0.5f, 0.25f)
                else -> Triple(cue.label, 0.5f, 0.45f)
            }
            is Cue.Distance -> labels += Triple(if (cue.closer) "Step closer" else "Step back", 0.5f, 0.42f)
            is Cue.Aim -> labels += Triple(if (cue.down) "Point the camera more down" else "Point the camera more forward", 0.5f, if (cue.down) 0.33f else 0.56f)
            is Cue.Zoom -> labels += Triple("${zoomLabel(cue.level)} shows this", 0.5f, 0.5f - 0.5f / cue.level + 0.04f)
            is Cue.Thing -> {
                val c = subjectBox?.let { Offset(it.centerX(), it.centerY()) } ?: Offset(0.5f, 0.45f)
                labels += Triple(cue.label, (c.x + cue.dir.x * 0.22f).coerceIn(0.2f, 0.8f), (c.y + cue.dir.y * 0.14f + 0.06f).coerceIn(0.1f, 0.7f))
            }
            null -> {}
        }

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val b = 10.dp.toPx() * bob
            when (cue) {
                is Cue.Edge -> {
                    val at = when (cue.dir) {
                        RIGHT -> Offset(w * 0.86f, h * 0.42f)
                        LEFT -> Offset(w * 0.14f, h * 0.42f)
                        UP -> Offset(w * 0.5f, h * 0.17f)
                        else -> Offset(w * 0.5f, h * 0.53f)
                    }
                    bigArrow(at + cue.dir * b, cue.dir, 64.dp.toPx())
                }
                is Cue.Aim -> {
                    val dir = if (cue.down) DOWN else UP
                    bigArrow(Offset(w * 0.5f, h * 0.45f) + dir * b, dir, 88.dp.toPx())
                }
                is Cue.Distance -> {
                    // Four corner brackets: growing outwards = step closer (things get bigger), shrinking = step back.
                    val k = if (cue.closer) 0.55f + 0.2f * bob else 0.75f - 0.2f * bob
                    val c = Offset(w * 0.5f, h * 0.42f)
                    val hw = w * 0.42f * k; val hh = h * 0.26f * k
                    brackets(c, hw, hh, 28.dp.toPx())
                }
                is Cue.Zoom -> {
                    val k = 0.5f / cue.level
                    val r = 3.dp.toPx()
                    drawRoundRect(
                        Color.White, Offset(w * (0.5f - k), h * (0.5f - k)), Size(w * 2 * k, h * 2 * k), CornerRadius(r * 4),
                        style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx()), 20.dp.toPx() * bob)),
                    )
                }
                is Cue.Thing -> {
                    val c = subjectBox?.let { Offset(it.centerX() * w, it.centerY() * h) } ?: Offset(w * 0.5f, h * 0.45f)
                    bigArrow(c + cue.dir * (w * 0.14f + b), cue.dir, 56.dp.toPx())
                }
                null -> {}
            }
        }
        labels.forEach { (text, fx, fy) -> CueLabel(text, fx, fy) }
    }
}

private fun zoomLabel(z: Float) = if (z < 1f) String.format(java.util.Locale.US, "%.1f×", z) else "${z.toInt()}×"

/** A dark pill with white text, centred on (fx, fy) of the view and kept on screen. */
@Composable
private fun CueLabel(text: String, fx: Float, fy: Float) {
    Box(
        Modifier.fillMaxSize().layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val margin = 12.dp.roundToPx()
                val x = (fx * constraints.maxWidth - p.width / 2f).toInt().coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
                val y = (fy * constraints.maxHeight - p.height / 2f).toInt().coerceIn(margin, (constraints.maxHeight - p.height - margin).coerceAtLeast(margin))
                p.place(x, y)
            }
        },
    ) {
        Box(Modifier.clip(CPShape.Pill).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(text, style = CPType.CaptionMedium, color = CP.OnDark)
        }
    }
}

/** A thick white arrow centred on [at], pointing along [dir], with a dark edge so it reads on any picture. */
private fun DrawScope.bigArrow(at: Offset, dir: Offset, len: Float) {
    val side = Offset(-dir.y, dir.x)
    val tip = at + dir * (len / 2)
    val tail = at - dir * (len / 2)
    val headLen = len * 0.42f
    val headW = len * 0.36f
    val shaftEnd = tip - dir * (headLen * 0.9f)
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        val l = tip - dir * headLen + side * headW
        val r = tip - dir * headLen - side * headW
        lineTo(l.x, l.y); lineTo(r.x, r.y); close()
    }
    val shaftW = len * 0.16f
    // dark edge first, then white
    drawLine(Color.Black.copy(alpha = 0.35f), tail, shaftEnd, shaftW + 5.dp.toPx(), StrokeCap.Round)
    drawPath(head, Color.Black.copy(alpha = 0.35f), style = Stroke(5.dp.toPx(), join = StrokeJoin.Round))
    drawLine(Color.White, tail, shaftEnd, shaftW, StrokeCap.Round)
    drawPath(head, Color.White)
}

/** Four L-shaped corners round a box centred on [c] (half sizes [hw], [hh]). */
private fun DrawScope.brackets(c: Offset, hw: Float, hh: Float, arm: Float) {
    val sw = 5.dp.toPx()
    for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
        val corner = c + Offset(sx * hw, sy * hh)
        val path = Path().apply {
            moveTo(corner.x - sx * arm, corner.y); lineTo(corner.x, corner.y); lineTo(corner.x, corner.y - sy * arm)
        }
        drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(sw + 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
