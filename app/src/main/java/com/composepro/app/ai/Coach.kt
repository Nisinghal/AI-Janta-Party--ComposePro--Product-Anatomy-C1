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
private const val SPOT = 0.08f
private const val SIZE_SLACK = 0.3f

/**
 * Is this move satisfied right now, judged by the phone itself? Null when the phone can't tell
 * (light, background, moving things), so only "Check my shot" can tick it.
 */
fun liveCheck(move: Move, tilt: Tilt, subject: Thing?): Boolean? = when (move.check) {
    CheckBy.Angle -> if (tilt == Tilt.Unknown) null else when (move.angle) {
        ShotAngle.Above -> tilt.offFlatDeg < 25f
        ShotAngle.Diner -> tilt.offFlatDeg in 20f..75f
        ShotAngle.Eye -> tilt.offFlatDeg > 60f
        null -> null
    }
    CheckBy.Frame -> subject?.let { s ->
        val onSpot = hypot(s.cx - (move.targetX ?: s.cx), s.cy - (move.targetY ?: s.cy)) < SPOT
        val size = move.size
        val sized = size == null || abs(s.box.height() - size) <= size * SIZE_SLACK + 0.05f
        onSpot && sized
    }
    CheckBy.Other -> null
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
