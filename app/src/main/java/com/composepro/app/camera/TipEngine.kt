package com.composepro.app.camera

import com.composepro.app.guide.Alignment
import com.composepro.app.guide.Guide
import com.composepro.app.guide.P
import com.composepro.app.guide.alignment
import com.composepro.app.guide.targets
import com.composepro.app.ui.EdgeState
import kotlin.math.abs
import kotlin.math.hypot

enum class TipKind { None, Note, Light, Framing, Guide, Good }

/**
 * What the camera should say right now. [key] identifies the problem so the screen can tell
 * "same problem, keep showing it" from "new problem, wait a second before showing it".
 */
data class TipDecision(
    val key: String,
    val kind: TipKind,
    val line1: String = "",
    val line2: String = "",
    val remind: String? = null,
    val edge: EdgeState = EdgeState.None,
    val main: Thing? = null,
    val alignment: Alignment? = null,
) {
    val isTip get() = kind == TipKind.Light || kind == TipKind.Framing || kind == TipKind.Guide

    companion object { val NONE = TipDecision("none", TipKind.None) }
}

/** Thresholds are first guesses from AX_SPEC/GUIDANCE, to be tuned on real food. */
object Thresholds {
    const val TOO_DARK = 40f
    const val WARM_CAST = 20f
    const val GLARE = 0.06f
    const val SHADOW = 60f
    const val EDGE_MARGIN = 0.01f
    const val TOO_CLOSE_AREA = 0.6f
    const val NOT_FLAT_DEG = 10f
    const val NOT_LEVEL_DEG = 7f
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

/** Priority: too dark → light → framing → guide → all good. One tip at a time (BRIEF.md). */
fun decide(r: FrameResult?, guide: Guide, tilt: Tilt, zoom: Float, dismissed: (Thing) -> Boolean): TipDecision {
    if (r == null) return TipDecision.NONE
    if (r.meanY < Thresholds.TOO_DARK) return TipDecision("dark", TipKind.Note, "Too dark for me to see. Try more light, or just shoot.")
    val main = r.things.firstOrNull() ?: return TipDecision.NONE   // not sure: no edge, no tip
    if (dismissed(main)) return TipDecision.NONE
    val looks = looksLike(main.category)
    fun line2(why: String) = if (looks != null) "$why · $looks" else why
    fun light(key: String, l1: String, why: String) =
        TipDecision(key, TipKind.Light, l1, line2(why), l1.replaceFirstChar { it.lowercase() }, EdgeState.Off, main)
    fun framing(key: String, l1: String, why: String) =
        TipDecision(key, TipKind.Framing, l1, line2(why), l1.replaceFirstChar { it.lowercase() }, EdgeState.Off, main)

    if (r.warmth > Thresholds.WARM_CAST) return light("warm", "Move it toward window light.", "The light here is turning it yellow")
    if (r.clipFrac > Thresholds.GLARE) return light("glare", "Tilt a little to dodge the glare.", "Bright spots are washing it out")
    if (r.centerY < Thresholds.SHADOW) return light("shadow", "Turn so the light falls on it.", "It's sitting in shadow")

    val b = main.box
    val m = Thresholds.EDGE_MARGIN
    if (b.left <= m || b.top <= m || b.right >= 1 - m || b.bottom >= 1 - m) return framing("cut", "Step back a little.", "It's cut off at the edge")
    if (main.area > Thresholds.TOO_CLOSE_AREA && zoom < 1.5f) return framing("close", "Step back and zoom to 2×.", "Up close, plates bend at the edges")
    if (tilt.flat && tilt.offFlatDeg > Thresholds.NOT_FLAT_DEG) return framing("flat", "Hold the phone flat above the table.", "From above works best straight down")
    if (!tilt.flat && tilt != Tilt.Unknown && abs(tilt.rollDeg) > Thresholds.NOT_LEVEL_DEG && abs(tilt.rollDeg) < 45f) return framing("level", "Hold the phone level.", "The table edge looks tilted")

    val points = r.things.map { P(it.cx, it.cy) }
    val al = alignment(guide, points)
    if (!al.done) {
        val t = targets(guide, points.size)
        val misses = al.matches.filter { !it.hit }
        val worst = misses.maxBy { m -> hypot(t[m.target].x - points[m.thing].x, t[m.target].y - points[m.thing].y) }
        val way = direction(t[worst.target].x - points[worst.thing].x, t[worst.target].y - points[worst.thing].y)
        val who = r.things[worst.thing].category?.let { "the $it" } ?: "it"
        val line1 = when {
            al.matches.size == 1 -> "Move $who $way onto the circle."
            misses.size == 1 -> "One more: move $who $way onto its circle."
            else -> "Follow the arrows: put each thing on a circle."
        }
        val line2 = if (al.matches.size == 1) "${guide.label} guide"
        else "${guide.label} · ${al.placed} of ${al.matches.size} in place"
        return TipDecision("guide-${guide.name}", TipKind.Guide, line1, line2,
            guide.tip.replaceFirstChar { it.lowercase() }, EdgeState.None, main, al)
    }
    return TipDecision("good", TipKind.Good, edge = EdgeState.Right, main = main, alignment = al)
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
