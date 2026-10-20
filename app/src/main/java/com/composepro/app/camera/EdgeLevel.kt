package com.composepro.app.camera

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How tilted the subject's own straight edges look in the picture (user test 2026-10-20: "the camera shows straight but
 * my laptop looks tilted"). The phone's level only knows gravity; a laptop on a sagging bed, a tray or a book can sit
 * at a slant, and what matters in the photo is that the subject's lines look straight.
 *
 * [deg]: the tilt of the strongest near-horizontal straight lines, + when their right end is lower. [skew]: the top and
 * bottom lines lean clearly different ways, so it's seen from the side (turning the phone won't fix that).
 *
 * Whole lines are fitted (a Hough transform over the edge points), not single pixels: at this small size a slanted edge
 * becomes little flat steps, which pixel-by-pixel reading took for level (checked on test edges at 0°, −3°, +6°, +12°:
 * within 0.25°; the laptop from the user's screenshot: −2.5°).
 */
data class EdgeTilt(val deg: Float, val skew: Boolean)

private const val MAX_DEG = 20f
private const val MIN_CONFIDENCE = 1.12f   // no straight lines ≈ 1.0; a laptop ≈ 1.2; a clean edge ≈ 4

/** Near-horizontal straight lines inside [box] (fractions of the view; the whole view when null), or null if none are clear. */
fun edgeTilt(g: Gray, box: RectF?): EdgeTilt? {
    val b = box ?: RectF(0f, 0f, 1f, 1f)
    val x0 = max(1, ((b.left - 0.03f) * g.w).toInt()); val x1 = min(g.w - 2, ((b.right + 0.03f) * g.w).toInt())
    val y0 = max(1, ((b.top - 0.03f) * g.h).toInt()); val y1 = min(g.h - 2, ((b.bottom + 0.03f) * g.h).toInt())
    if (x1 - x0 < 16 || y1 - y0 < 16) return null
    // Edge points that run roughly sideways, with their strength.
    var n = 0
    val xs = IntArray((x1 - x0 + 1) * (y1 - y0 + 1)); val ys = IntArray(xs.size); val ms = FloatArray(xs.size)
    for (y in y0..y1) for (x in x0..x1) {
        val gx = (g.at(x + 1, y - 1) + 2 * g.at(x + 1, y) + g.at(x + 1, y + 1)) - (g.at(x - 1, y - 1) + 2 * g.at(x - 1, y) + g.at(x - 1, y + 1))
        val gy = (g.at(x - 1, y + 1) + 2 * g.at(x, y + 1) + g.at(x + 1, y + 1)) - (g.at(x - 1, y - 1) + 2 * g.at(x, y - 1) + g.at(x + 1, y - 1))
        if (abs(gy) < 1.5f * abs(gx)) continue
        val mag = sqrt((gx * gx + gy * gy).toFloat())
        if (mag < 90f) continue
        xs[n] = x; ys[n] = y; ms[n] = mag; n++
    }
    if (n < 40) return null
    val all = line(xs, ys, ms, n, g, null) ?: return null
    if (all.second < MIN_CONFIDENCE) return null
    val mid = (y0 + y1) / 2
    val top = line(xs, ys, ms, n, g) { it < mid }
    val bottom = line(xs, ys, ms, n, g) { it >= mid }
    val skew = top != null && bottom != null && top.second >= MIN_CONFIDENCE && bottom.second >= MIN_CONFIDENCE && abs(top.first - bottom.first) > 5f
    return EdgeTilt(all.first, skew)
}

/** Best line angle (coarse 1° steps, then 0.25° round the best) and how much it stands out (score ÷ average score). */
private fun line(xs: IntArray, ys: IntArray, ms: FloatArray, n: Int, g: Gray, rows: ((Int) -> Boolean)?): Pair<Float, Float>? {
    val offset = g.w + 2
    val bins = FloatArray(g.h + 2 * g.w + 4)
    fun score(deg: Float): Float {
        val r = Math.toRadians(deg.toDouble()); val s = sin(r).toFloat(); val c = cos(r).toFloat()
        java.util.Arrays.fill(bins, 0f)
        for (i in 0 until n) {
            if (rows != null && !rows(ys[i])) continue
            val rho = (ys[i] * c - xs[i] * s).roundToInt() + offset
            if (rho in bins.indices) bins[rho] += ms[i]
        }
        var sum = 0f
        for (v in bins) sum += v * v
        return sum
    }
    var best = 0f; var bestScore = -1f; var total = 0f; var count = 0
    var d = -MAX_DEG
    while (d <= MAX_DEG) { val sc = score(d); total += sc; count++; if (sc > bestScore) { bestScore = sc; best = d }; d += 1f }
    if (bestScore <= 0f) return null
    val mean = total / count
    var fine = best; var fineScore = bestScore
    var f = best - 0.75f
    while (f <= best + 0.75f) { val sc = score(f); if (sc > fineScore) { fineScore = sc; fine = f }; f += 0.25f }
    return fine to (fineScore / mean)
}
