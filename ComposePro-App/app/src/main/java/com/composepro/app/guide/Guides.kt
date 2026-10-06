package com.composepro.app.guide

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/** The guides from GUIDANCE.md. Positions are fractions of the camera view (0..1). The camera picks one after a 3-second look (Picker.kt). */
enum class Guide(val label: String, val tip: String) {
    Centre("Centre", "Nudge it a little higher, just above the middle."),
    Thirds("Thirds", "Move it onto one of the cross points."),
    FrontBack("Front & back", "Big one in front, small one behind."),
    Diagonal("Diagonal", "Line them up along the diagonal."),
    Triangle("Triangle", "Put the three things on the triangle's points."),
    Grid("Grid", "Line them up in neat rows."),
    Circle("Circle", "Biggest in the middle, the rest around it."),
    Spiral("Golden spiral", "Put the main thing in the spiral's eye."),
}

data class P(val x: Float, val y: Float)

object Spiral {
    const val EX = 0.62f
    const val EY = 0.40f
    private const val A = 0.02f
    private val B = (ln(1.618) / (Math.PI / 2)).toFloat()

    /** A point on a golden (logarithmic) spiral, in pixels, for a view of w x h. */
    fun point(t: Float, w: Float, h: Float): P {
        val r = A * min(w, h) * exp(B * t)
        return P(EX * w + r * cos(t), EY * h + r * sin(t))
    }

    fun maxT(w: Float, h: Float): Float = ln(maxOf(w, h) * 0.9f / (A * min(w, h))) / B

    /** Spots along the curve for the supporting things, as fractions of the 3:4 view. */
    val along: List<P> = listOf(7.6f, 8.9f, 10.0f, 10.7f, 11.2f).map { t ->
        val p = point(t, 3f, 4f)
        P((p.x / 3f).coerceIn(0.15f, 0.85f), (p.y / 4f).coerceIn(0.15f, 0.85f))
    }
}

/** Circle guide: the hero's spot and the ring the others sit on (looks round on the 3:4 view). */
object Ring {
    val centre = P(0.5f, 0.48f)
    const val RX = 0.30f
    const val RY = 0.225f
}

/** Where things should go. slots = how many things the guide is laid out for (from the 3-second look). [0] is the hero's spot where that matters. */
fun targets(guide: Guide, slots: Int): List<P> = when (guide) {
    Guide.Centre -> listOf(P(0.5f, 0.45f))
    Guide.Thirds -> listOf(P(1 / 3f, 1 / 3f), P(2 / 3f, 1 / 3f), P(1 / 3f, 2 / 3f), P(2 / 3f, 2 / 3f))
    Guide.FrontBack -> listOf(P(0.42f, 0.63f), P(0.62f, 0.36f))
    Guide.Triangle -> listOf(P(0.5f, 0.3f), P(0.28f, 0.68f), P(0.72f, 0.68f))
    Guide.Diagonal -> {
        val k = slots.coerceIn(2, 3)
        List(k) { i -> val t = (i + 0.5f) / k; P(0.18f + t * 0.64f, 0.24f + t * 0.52f) }
    }
    Guide.Grid -> when {
        slots <= 4 -> listOf(P(0.33f, 0.35f), P(0.67f, 0.35f), P(0.33f, 0.63f), P(0.67f, 0.63f))
        slots == 5 -> listOf(P(0.3f, 0.3f), P(0.7f, 0.3f), P(0.5f, 0.49f), P(0.3f, 0.68f), P(0.7f, 0.68f))
        else -> listOf(0.25f, 0.5f, 0.75f).flatMap { y -> listOf(P(0.33f, y), P(0.67f, y)) }
    }
    Guide.Circle -> {
        val k = (slots - 1).coerceIn(2, 5)
        listOf(Ring.centre) + List(k) { i ->
            val a = -PI / 2 + 2 * PI * i / k
            P(Ring.centre.x + Ring.RX * cos(a).toFloat(), Ring.centre.y + Ring.RY * sin(a).toFloat())
        }
    }
    Guide.Spiral -> listOf(P(Spiral.EX, Spiral.EY)) + Spiral.along.take((slots - 1).coerceIn(0, 5))
}

/** One thing paired with the guide point it should move to. Indexes into the things and targets lists. */
data class Match(val thing: Int, val target: Int, val hit: Boolean)

data class Alignment(val hits: List<Boolean>, val done: Boolean, val matches: List<Match> = emptyList()) {
    val placed get() = matches.count { it.hit }
}

private const val TOLERANCE = 0.08f
private fun dist(a: P, b: P) = hypot(a.x - b.x, a.y - b.y)

/**
 * Which thing goes to which guide point, and is it there yet? things are largest first, so things[0] is the hero.
 * Guides with a hero spot (front & back, circle, spiral) send the biggest thing there; the rest are paired
 * so the total moving is as small as possible, so every thing gets its own spot.
 */
fun alignment(guide: Guide, things: List<P>, slots: Int = things.size): Alignment {
    val t = targets(guide, slots)
    if (things.isEmpty()) return Alignment(t.map { false }, false)
    val used = things.take(slots.coerceAtLeast(1))
    val pairs: List<Pair<Int, Int>> = when (guide) {
        Guide.Centre -> listOf(0 to 0)
        Guide.Thirds -> listOf(0 to t.indices.minBy { dist(used[0], t[it]) })
        Guide.FrontBack, Guide.Circle, Guide.Spiral ->
            listOf(0 to 0) + bestPairs(used.drop(1), t.drop(1)).map { (i, j) -> i + 1 to j + 1 }
        else -> bestPairs(used, t)
    }
    val matches = pairs.map { (i, j) -> Match(i, j, dist(used[i], t[j]) < TOLERANCE) }
    val hits = t.indices.map { j -> matches.any { it.target == j && it.hit } }
    return Alignment(hits, matches.all { it.hit }, matches)
}

/** Tries every pairing of things with points (at most 6 × 6 = 720 tries, so this is instant). */
private fun bestPairs(things: List<P>, t: List<P>): List<Pair<Int, Int>> {
    val n = min(things.size, t.size)
    if (n == 0) return emptyList()
    var best = emptyList<Int>()
    var bestCost = Float.MAX_VALUE
    fun go(chosen: List<Int>) {
        if (chosen.size == n) {
            val cost = chosen.indices.sumOf { dist(things[it], t[chosen[it]]).toDouble() }.toFloat()
            if (cost < bestCost) { bestCost = cost; best = chosen }
            return
        }
        for (j in t.indices) if (j !in chosen) go(chosen + j)
    }
    go(emptyList())
    return best.mapIndexed { i, j -> i to j }
}
