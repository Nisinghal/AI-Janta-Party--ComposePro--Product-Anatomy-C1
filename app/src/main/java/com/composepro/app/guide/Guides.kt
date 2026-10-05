package com.composepro.app.guide

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/** The guides from GUIDANCE.md. Positions are fractions of the camera view (0..1). */
enum class Guide(val label: String, val tip: String) {
    Centre("Centre", "Nudge it a little higher, just above the middle."),
    Thirds("Thirds", "Move it onto one of the cross points."),
    Triangle("Triangle", "Put the three things on the triangle's points."),
    Diagonal("Diagonal", "Line them up along the diagonal."),
    Spiral("Golden spiral", "Put the main dish in the spiral's eye.");

    companion object {
        /** Auto-pick from how many things are in frame (decided 2026-10-04). */
        fun autoFor(count: Int): Guide = when {
            count <= 1 -> Centre
            count == 2 -> Diagonal
            count == 3 -> Triangle
            else -> Spiral
        }
    }
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
}

fun targets(guide: Guide, count: Int): List<P> = when (guide) {
    Guide.Centre -> listOf(P(0.5f, 0.45f))
    Guide.Thirds -> listOf(P(1 / 3f, 1 / 3f), P(2 / 3f, 1 / 3f), P(1 / 3f, 2 / 3f), P(2 / 3f, 2 / 3f))
    Guide.Triangle -> listOf(P(0.5f, 0.3f), P(0.28f, 0.68f), P(0.72f, 0.68f))
    Guide.Diagonal -> {
        val k = count.coerceIn(2, 3)
        List(k) { i -> val t = (i + 0.5f) / k; P(0.18f + t * 0.64f, 0.24f + t * 0.52f) }
    }
    Guide.Spiral -> listOf(P(Spiral.EX, Spiral.EY))
}

data class Alignment(val hits: List<Boolean>, val done: Boolean)

/** Are the things sitting on the guide's points? things[0] is the main thing (largest). */
fun alignment(guide: Guide, things: List<P>): Alignment {
    val t = targets(guide, things.size)
    if (things.isEmpty()) return Alignment(t.map { false }, false)
    val tol = 0.08f
    fun near(a: P, b: P) = hypot(a.x - b.x, a.y - b.y) < tol
    return when (guide) {
        Guide.Centre, Guide.Spiral -> { val h = near(things[0], t[0]); Alignment(listOf(h), h) }
        Guide.Thirds -> { val hits = t.map { near(things[0], it) }; Alignment(hits, hits.any { it }) }
        else -> {
            val used = mutableSetOf<Int>()
            val hits = t.map { target ->
                val j = things.indices.firstOrNull { it !in used && near(things[it], target) }
                if (j != null) { used += j; true } else false
            }
            Alignment(hits, hits.count { it } >= min(t.size, things.size))
        }
    }
}
