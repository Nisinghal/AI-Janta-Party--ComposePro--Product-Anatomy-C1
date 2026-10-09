package com.composepro.app.ai

import android.graphics.RectF
import com.composepro.app.camera.Thing
import com.composepro.app.camera.Tilt
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The photographer's plan in progress. While it's on screen, live tips are hidden (one voice at a time,
 * user feedback 2026-10-06). [checked] and [notes] come from the photographer's last automatic look; [subjectId] is the
 * on-phone tracked thing that matches the photographer's subject, so frame moves can be checked live.
 */
data class Coach(
    val advice: Advice,
    val subjectId: Int?,
    /**
     * Per step: can the phone's own check be believed? Not if it already passed when the advice arrived: the
     * photographer still asked for it, so the phone is measuring the wrong thing (e.g. "lower the phone" is about
     * height, the tilt sensor only knows angle; phone test 2026-10-06). Those steps wait for the photographer's look.
     */
    val liveTrusted: List<Boolean> = List(advice.moves.size) { true },
    val checked: List<Boolean> = List(advice.moves.size) { false },
    val notes: List<String> = List(advice.moves.size) { "" },
    val next: String? = null,
    val ready: Boolean = advice.ready,
)

/** How close (fraction of the frame) the subject's centre must be to the ring, and how close its size must be. */
// Group test 2026-10-07 ("both steps never complete"): 0.08 / 15% was hard to hold with a hand-held phone,
// and the subject's box comes from the photographer's rough outline. 0.10 / 20% is still tight enough to mean it.
private const val SPOT = 0.10f
private const val SIZE_SLACK = 0.20f

/**
 * Is this move satisfied right now, judged by the phone itself? Null when the phone can't tell
 * (light, background, moving things), so only "Check my shot" can tick it.
 */
fun liveCheck(move: Move, tilt: Tilt, subject: Thing?, zoom: Float): Boolean? = when (move.check) {
    CheckBy.Zoom -> move.zoom?.let { abs(zoom - it) <= it * 0.15f }
    CheckBy.Angle -> if (tilt == Tilt.Unknown) null else when (move.angle) {
        ShotAngle.Above -> tilt.offFlatDeg < 25f
        ShotAngle.Diner -> tilt.offFlatDeg in 20f..75f
        ShotAngle.Eye -> tilt.offFlatDeg > 60f
        null -> null
    }
    CheckBy.Frame -> subject?.let { s ->
        val onSpot = hypot(s.cx - (move.targetX ?: s.cx), s.cy - (move.targetY ?: s.cy)) < SPOT
        val size = move.size
        // Within ~15% of the asked-for height (was 30% + 0.05: a laptop at 60% passed for 85%, phone test 2026-10-06).
        val sized = size == null || abs(s.box.height() - size) <= size * SIZE_SLACK + 0.02f
        onSpot && sized
    }
    CheckBy.Other -> null
}

/**
 * An angle step in words nobody can misread (group feedback 2026-10-09: "tilt the phone" — which way?). The target,
 * said as where the camera points; the live hint ([angleHint]) says which way to go from where the phone is now.
 */
fun angleAction(angle: ShotAngle): String = when (angle) {
    ShotAngle.Above -> "Hold the phone flat over it, camera pointing straight down"
    ShotAngle.Diner -> "Point the camera down at it at a slant, like when you sit at a table"
    ShotAngle.Eye -> "Hold the phone upright, camera pointing straight at it"
}

/** Which way to point the camera from the phone's tilt right now, or that it's right. Null when the tilt is unknown. */
fun angleHint(angle: ShotAngle?, tilt: Tilt): String? {
    if (angle == null || tilt == Tilt.Unknown) return null
    val d = tilt.offFlatDeg   // 0 = camera straight down, 90 = phone upright
    val down = "Point the camera more down ↓"
    val forward = "Point the camera more forward ↑"
    val right = "That's it. Hold it there."
    return when (angle) {
        ShotAngle.Above -> if (d > 25f) down else right
        ShotAngle.Diner -> when { d < 20f -> forward; d > 75f -> down; else -> right }
        ShotAngle.Eye -> if (d < 60f) forward else right
    }
}

/**
 * Which way to move, in the app's fixed plain words, from where the subject is (dx, dy = subject minus circle, as
 * fractions of the view). The phone goes towards the subject; the subject then slides into the circle.
 */
fun moveWords(dx: Float, dy: Float, kind: ShotKind): String = if (abs(dx) >= abs(dy)) {
    val side = if (dx > 0) "right" else "left"
    if (kind == ShotKind.TableTop) "Move the phone a little $side" else "Step $side"
} else if (dy < 0) "Point the camera a little higher" else "Point the camera a little lower"

/** The live line under a position step, from the dot and the circle right now, so it always matches what's drawn. */
fun frameHint(subject: Thing, move: Move, kind: ShotKind): String? {
    val tx = move.targetX ?: return null
    val ty = move.targetY ?: return null
    val dx = subject.cx - tx; val dy = subject.cy - ty
    if (hypot(dx, dy) >= SPOT) return "${moveWords(dx, dy, kind)} until the dot is in the circle."
    val size = move.size ?: return "That's it. Hold it there."
    val h = subject.box.height()
    return when {
        h < size * (1 - SIZE_SLACK) -> "Now step a little closer, until it fills the white box."
        h > size * (1 + SIZE_SLACK) -> "Now step back a little, until it fits the white box."
        else -> "That's it. Hold it there."
    }
}

/** The on-phone thing that best overlaps [box] (the photographer's subject), if any overlaps enough. */
fun matchSubject(box: RectF?, things: List<Thing>): Thing? {
    if (box == null) return things.firstOrNull()
    return things.maxByOrNull { overlap(it.box, box) }?.takeIf { overlap(it.box, box) > 0.2f }
}

private fun overlap(a: RectF, b: RectF): Float {
    val iw = min(a.right, b.right) - max(a.left, b.left)
    val ih = min(a.bottom, b.bottom) - max(a.top, b.top)
    if (iw <= 0 || ih <= 0) return 0f
    val inter = iw * ih
    return inter / (a.width() * a.height() + b.width() * b.height() - inter)
}
