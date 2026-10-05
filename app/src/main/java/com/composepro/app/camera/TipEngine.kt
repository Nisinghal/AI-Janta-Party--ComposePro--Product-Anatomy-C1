package com.composepro.app.camera

import com.composepro.app.guide.Alignment
import com.composepro.app.guide.Guide
import com.composepro.app.guide.P
import com.composepro.app.guide.alignment
import com.composepro.app.ui.EdgeState
import kotlin.math.abs

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

private val articleWords = setOf("cup", "dish", "drink", "cake", "plate", "bowl", "sandwich", "burger", "pie", "cookie", "snack", "salad", "pizza")

fun looksLike(label: String?, category: String?): String {
    val word = (label ?: category?.takeIf { it.equals("Food", true) })?.lowercase() ?: return "Looks like food"
    return if (word in articleWords) "Looks like a $word" else "Looks like $word"
}

/** Priority: too dark → light → framing → guide → all good. One tip at a time (BRIEF.md). */
fun decide(r: FrameResult?, guide: Guide, tilt: Tilt, zoom: Float, dismissed: (Thing) -> Boolean): TipDecision {
    if (r == null) return TipDecision.NONE
    if (r.meanY < Thresholds.TOO_DARK) return TipDecision("dark", TipKind.Note, "Too dark for me to see. Try more light, or just shoot.")
    val main = r.things.firstOrNull() ?: return TipDecision.NONE   // not sure: no edge, no tip
    if (dismissed(main)) return TipDecision.NONE
    val looks = looksLike(r.label, main.category)
    fun light(key: String, l1: String, why: String) =
        TipDecision(key, TipKind.Light, l1, "$why · $looks", l1.replaceFirstChar { it.lowercase() }, EdgeState.Off, main)
    fun framing(key: String, l1: String, why: String) =
        TipDecision(key, TipKind.Framing, l1, "$why · $looks", l1.replaceFirstChar { it.lowercase() }, EdgeState.Off, main)

    if (r.warmth > Thresholds.WARM_CAST) return light("warm", "Move it toward window light.", "The light here is turning it yellow")
    if (r.clipFrac > Thresholds.GLARE) return light("glare", "Tilt a little to dodge the glare.", "Bright spots are washing it out")
    if (r.centerY < Thresholds.SHADOW) return light("shadow", "Turn so the light falls on it.", "It's sitting in shadow")

    val b = main.box
    val m = Thresholds.EDGE_MARGIN
    if (b.left <= m || b.top <= m || b.right >= 1 - m || b.bottom >= 1 - m) return framing("cut", "Step back a little.", "It's cut off at the edge")
    if (main.area > Thresholds.TOO_CLOSE_AREA && zoom < 1.5f) return framing("close", "Step back and zoom to 2×.", "Up close, plates bend at the edges")
    if (tilt.flat && tilt.offFlatDeg > Thresholds.NOT_FLAT_DEG) return framing("flat", "Hold the phone flat above the table.", "From above works best straight down")
    if (!tilt.flat && tilt != Tilt.Unknown && abs(tilt.rollDeg) > Thresholds.NOT_LEVEL_DEG && abs(tilt.rollDeg) < 45f) return framing("level", "Hold the phone level.", "The table edge looks tilted")

    val al = alignment(guide, r.things.map { P(it.cx, it.cy) })
    if (!al.done) {
        return TipDecision("guide-${guide.name}", TipKind.Guide, guide.tip, "Guide: ${guide.label} · Swipe to change",
            guide.tip.replaceFirstChar { it.lowercase() }, EdgeState.None, main, al)
    }
    return TipDecision("good", TipKind.Good, edge = EdgeState.Right, main = main, alignment = al)
}
