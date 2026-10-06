package com.composepro.app.camera

import android.graphics.RectF
import com.composepro.app.guide.Alignment
import com.composepro.app.guide.Angle
import com.composepro.app.guide.GuidePick
import com.composepro.app.guide.P
import com.composepro.app.guide.alignment
import com.composepro.app.guide.angleFor
import com.composepro.app.guide.animals
import com.composepro.app.guide.targets
import com.composepro.app.ui.EdgeState
import kotlin.math.abs
import kotlin.math.hypot

enum class TipKind { None, Note, Light, Framing, Guide, Good }

/**
 * What the camera should say right now. Two rows can show at once (user decision, 2026-10-06: "both, always"):
 * [phone] is how to hold or move the phone, [arrange] is where to move the things themselves.
 * [key] identifies the problem so the screen can tell "same problem, keep showing it"
 * from "new problem, wait a second before showing it".
 */
data class TipDecision(
    val key: String,
    val kind: TipKind,
    val phone: String = "",
    val why: String = "",
    val arrange: String? = null,
    val remind: String? = null,
    val edge: EdgeState = EdgeState.None,
    val main: Thing? = null,
    val alignment: Alignment? = null,
) {
    val isTip get() = kind == TipKind.Light || kind == TipKind.Framing || kind == TipKind.Guide

    companion object { val NONE = TipDecision("none", TipKind.None) }
}

/** Thresholds are first guesses from AX_SPEC/GUIDANCE, tuned after the 2026-10-06 phone test. */
object Thresholds {
    const val TOO_DARK = 40f
    const val WARM_CAST = 20f
    const val GLARE = 0.06f
    const val SHADOW = 60f
    const val EDGE_MARGIN = 0.01f
    const val TOO_CLOSE_AREA = 0.7f
    const val TOO_SMALL_AREA = 0.06f
    const val NOT_LEVEL_DEG = 7f
    /** How far the subject (or a group's middle) may sit from its spot before the phone tip asks to move. */
    const val SPOT_TOLERANCE = 0.08f
    const val GROUP_TOLERANCE = 0.15f
}

/** Words that read naturally without "a": "Looks like pizza", not "Looks like a pizza". */
private val massWords = setOf("pizza", "broccoli", "cake", "food", "rice", "coffee", "bread")

/** "a cup", "an orange", "pizza". */
private fun withArticle(word: String): String = when {
    word in massWords || word.endsWith("s") -> word
    word.first().lowercaseChar() in "aeiou" -> "an $word"
    else -> "a $word"
}

/** "Looks like a cup", or null if the camera has no word for it (AX_SPEC: wrong is worse than quiet). */
fun looksLike(name: String?): String? = name?.let { "Looks like ${withArticle(it)}" }

/** The name used in Review and the "not right" list, e.g. "Cup", "Laptop". */
fun thingName(name: String?): String = name?.replaceFirstChar { it.uppercase() } ?: "Photo"

private class PhoneTip(val key: String, val kind: TipKind, val text: String, val why: String)

/**
 * Phone row priority: light → angle → level → distance → where the subject sits.
 * Arrange row (2+ things): which thing to move where. One phone tip at a time; the arrange row shows alongside it.
 */
fun decide(r: FrameResult?, pick: GuidePick, tilt: Tilt, zoom: Float, dismissed: (Thing) -> Boolean): TipDecision {
    if (r == null) return TipDecision.NONE
    if (r.meanY < Thresholds.TOO_DARK) return TipDecision("dark", TipKind.Note, "Too dark for me to see. Try more light, or just shoot.")
    // One subject: follow the same object it picked, even if it isn't the biggest this frame or its name wobbles.
    val main = r.things.firstOrNull { pick.slots == 1 && it.id == pick.mainId } ?: r.things.firstOrNull()
        ?: return TipDecision.NONE   // not sure: no edge, no tip
    if (dismissed(main)) return TipDecision.NONE

    val used = if (pick.slots == 1) listOf(main) else r.things.take(pick.slots)
    val group = RectF(used[0].box).apply { used.drop(1).forEach { union(it.box) } }
    val single = used.size == 1
    val subject = if (single) main else Thing(null, group, null)
    val name = if (single) main.category?.let { "the $it" } ?: "it" else "them"
    val points = used.map { P(it.cx, it.cy) }
    val al = alignment(pick.guide, points, pick.slots)
    val spots = targets(pick.guide, pick.slots)

    val phone = phoneTip(r, pick, tilt, zoom, group, name, al, spots, used)
    val arrange = if (pick.arranging && used.size >= 2) arrangeTip(used, al, spots, points) else null

    if (phone == null && arrange == null) {
        return TipDecision("good", TipKind.Good, edge = EdgeState.Right, main = subject, alignment = al)
    }
    val looks = if (single) looksLike(main.category) else null
    return TipDecision(
        key = "${phone?.key ?: "-"}|${if (arrange != null) "arrange" else "-"}",
        kind = phone?.kind ?: TipKind.Guide,
        phone = phone?.text ?: "",
        why = listOfNotNull(phone?.why, looks).joinToString(" · "),
        arrange = arrange,
        remind = (phone?.text ?: arrange)?.replaceFirstChar { it.lowercase() },
        edge = EdgeState.Off,
        main = subject,
        alignment = al,
    )
}

private fun phoneTip(
    r: FrameResult, pick: GuidePick, tilt: Tilt, zoom: Float, group: RectF, name: String,
    al: Alignment, spots: List<P>, used: List<Thing>,
): PhoneTip? {
    fun light(key: String, text: String, why: String) = PhoneTip(key, TipKind.Light, text, why)
    fun frame(key: String, text: String, why: String) = PhoneTip(key, TipKind.Framing, text, why)

    if (r.warmth > Thresholds.WARM_CAST) return light("warm", "Move it toward window light.", "The light here is turning it yellow")
    if (r.clipFrac > Thresholds.GLARE) return light("glare", "Tilt a little to dodge the glare.", "Bright spots are washing it out")
    if (r.centerY < Thresholds.SHADOW) return light("shadow", "Turn so the light falls on it.", "It's sitting in shadow")

    // Angle: offFlatDeg is 0 when the phone points straight down and 90 when it's upright.
    if (tilt != Tilt.Unknown) {
        val off = tilt.offFlatDeg
        // One subject: the angle follows what's in view now, not what was there when the guide was picked.
        val single = used.size == 1
        val animal = single && used[0].category in animals
        when (if (single) angleFor(used[0].category) else pick.angle) {
            Angle.Above -> if (off > 25f) return frame("angle", "Hold the phone flat above it.", "Flat things look best from straight above")
            Angle.Eye -> if (off < 60f) return frame(
                "angle",
                if (animal) "Get down to its eye level." else "Lower the phone to $name's height.",
                if (animal) "Animals look best at their own eye level" else "Tall things look best from the side",
            )
            Angle.Diner -> if (off < 20f || off > 75f) return frame("angle", "Tilt the phone, like you're sitting at the table.", "A 45° angle shows the top and the side")
            Angle.Any -> Unit
        }
        if (!tilt.flat && abs(tilt.rollDeg) > Thresholds.NOT_LEVEL_DEG && abs(tilt.rollDeg) < 45f) {
            return frame("level", "Hold the phone level.", "The picture looks tilted")
        }
    }

    val m = Thresholds.EDGE_MARGIN
    if (group.left <= m || group.top <= m || group.right >= 1 - m || group.bottom >= 1 - m) {
        return frame("distance", "Step back a little.", "Part of it is cut off at the edge")
    }
    val area = group.width() * group.height()
    if (area > Thresholds.TOO_CLOSE_AREA && zoom < 1.5f) return frame("distance", "Step back and zoom to 2×.", "Up close, things bend at the edges")
    if (area < Thresholds.TOO_SMALL_AREA) return frame("distance", "Move closer.", "It's small in the frame")

    // Where it sits. One thing: move the phone until it lands on its circle. A group: keep its middle near the centre.
    val from: P
    val to: P
    val tol: Float
    if (used.size == 1) {
        val m0 = al.matches.firstOrNull() ?: return null
        from = P(used[0].cx, used[0].cy); to = spots[m0.target]; tol = Thresholds.SPOT_TOLERANCE
    } else {
        from = P(group.centerX(), group.centerY()); to = P(0.5f, 0.5f); tol = Thresholds.GROUP_TOLERANCE
    }
    val dx = to.x - from.x
    val dy = to.y - from.y
    if (hypot(dx, dy) < tol) return null
    val move = phoneMove(dx, dy, tilt.flat)
    val why = if (used.size == 1) "So $name sits on the circle" else "So the group sits in the middle"
    return frame("move", move, why)   // one key for every direction, so the card stays up and its words update live
}

/**
 * Turns "the subject needs to move this way in the picture" into a phone move. Moving the phone left
 * moves everything in the picture right. Up/down depends on how it's held: aim lower/higher when upright,
 * toward/away from you when it's flat above a table.
 */
private fun phoneMove(dx: Float, dy: Float, flat: Boolean): String {
    val h = when { dx > 0.03f -> "left"; dx < -0.03f -> "right"; else -> null }
    val v = when {
        dy < -0.03f -> if (flat) "toward you" else "aim a little lower"
        dy > 0.03f -> if (flat) "away from you" else "aim a little higher"
        else -> null
    }
    return when {
        h != null && v != null && flat -> "Move the phone $h and $v."
        h != null && v != null -> "Move the phone $h and $v."
        h != null -> "Move the phone $h."
        v != null && flat -> "Move the phone $v."
        v != null -> "${v.replaceFirstChar { it.uppercase() }}."
        else -> "Move the phone a little."
    }
}

/** Arrange row: which thing goes where. Null when everything sits on its circle. */
private fun arrangeTip(used: List<Thing>, al: Alignment, spots: List<P>, points: List<P>): String? {
    val misses = al.matches.filter { !it.hit }
    if (misses.isEmpty()) return null
    val worst = misses.maxBy { m -> hypot(spots[m.target].x - points[m.thing].x, spots[m.target].y - points[m.thing].y) }
    val way = direction(spots[worst.target].x - points[worst.thing].x, spots[worst.target].y - points[worst.thing].y)
    val who = used[worst.thing].category?.let { "the $it" } ?: "it"
    return if (misses.size == 1) "Move $who $way onto its circle."
    else "Move $who $way onto its circle (${al.placed} of ${al.matches.size} done)."
}

/** "up and to the left", "down", "a little to the right". dx/dy are in frame fractions, screen directions. */
private fun direction(dx: Float, dy: Float): String {
    val v = when { dy < -0.03f -> "up"; dy > 0.03f -> "down"; else -> null }
    val h = when { dx < -0.03f -> "to the left"; dx > 0.03f -> "to the right"; else -> null }
    return when {
        v != null && h != null -> "$v and $h"
        v != null -> v
        h != null -> h
        else -> "a little"
    }
}
