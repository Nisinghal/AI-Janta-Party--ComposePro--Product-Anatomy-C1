package com.composepro.app.camera

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A small upright greyscale copy of one camera frame (0–255 per pixel), the same 3:4 framing as the preview. */
class Gray(val w: Int, val h: Int, val px: ByteArray) {
    fun at(x: Int, y: Int): Int = px[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)].toInt() and 0xFF
}

/**
 * Follows one subject by how it looks, not by what it is, so it works for things the detector has no name for
 * (a Buddha statue, a flower vase; phone test 2026-10-06: "how will I know if it's in the centre?").
 * The photographer says where the subject is; a small patch of that spot is remembered, and each new frame is
 * searched nearby for the best match (normalised correlation, so a change in brightness doesn't throw it off).
 */
class Tracker {
    private val tw = 20
    private val th = 20
    private var tpl = FloatArray(0)
    private var cx = 0f
    private var cy = 0f
    private var bw = 0f
    private var bh = 0f
    private var w = 1
    private var h = 1
    var locked = false
        private set

    /** How well the last search matched (−1..1); kept for the log, to see why tracking holds or drops. */
    var lastScore = 0f
        private set

    /** Where the subject is now, as fractions of the frame, or null if it's been lost. */
    val box: RectF?
        get() = if (!locked) null else RectF((cx - bw / 2) / w, (cy - bh / 2) / h, (cx + bw / 2) / w, (cy + bh / 2) / h)

    fun stop() { locked = false; tpl = FloatArray(0) }

    /** Remember the subject at [box] (fractions) in [then], then find it in [now] anywhere in the frame. */
    fun start(then: Gray, box: RectF, now: Gray): Boolean {
        w = then.w; h = then.h
        bw = (box.width() * w).coerceIn(6f, w.toFloat())
        bh = (box.height() * h).coerceIn(6f, h.toFloat())
        cx = box.centerX() * w
        cy = box.centerY() * h
        tpl = sample(then, cx, cy, bw, bh) ?: return false.also { locked = false }
        locked = true
        // The answer arrives seconds after the frame was taken, so first look for it across the whole frame.
        val coarse = best(now, cx, cy, step = 3, radiusX = w, radiusY = h, scales = floatArrayOf(0.6f, 0.75f, 0.9f, 1f, 1.15f, 1.35f, 1.6f), wholeFrame = true, pull = 0.6f)
        lastScore = coarse?.score ?: -1f
        if (coarse == null || coarse.score < START_SCORE) { locked = false; return false }
        apply(coarse)
        best(now, cx, cy, step = 1, radiusX = 3, radiusY = 3, scales = floatArrayOf(1f))?.let { if (it.score >= MIN_SCORE) apply(it) }
        return true
    }

    /** Follow it into the next frame. Stays where it was (and reports lost) if nothing nearby matches well enough. */
    fun update(now: Gray) {
        if (!locked || tpl.isEmpty()) return
        val m = best(now, cx, cy, step = 1, radiusX = 10, radiusY = 10, scales = floatArrayOf(0.92f, 1f, 1.08f))
        lastScore = m?.score ?: -1f
        if (m == null || m.score < MIN_SCORE) {
            // One more try further out, in case the phone moved quickly.
            val wide = best(now, cx, cy, step = 2, radiusX = 28, radiusY = 28, scales = floatArrayOf(1f))
            if (wide == null || wide.score < MIN_SCORE) { locked = false; return }
            apply(wide)
        } else apply(m)
    }

    private class Match(val x: Float, val y: Float, val scale: Float, val score: Float)

    private fun apply(m: Match) {
        cx = m.x; cy = m.y
        bw = (bw * m.scale).coerceIn(6f, w.toFloat()); bh = (bh * m.scale).coerceIn(6f, h.toFloat())
    }

    /**
     * Best match around (x0, y0). With [pull] > 0, matches far from (x0, y0) are marked down, so a look-alike patch
     * across the frame doesn't win over the subject itself (it locked onto the keyboard once, 2026-10-06).
     */
    private fun best(g: Gray, x0: Float, y0: Float, step: Int, radiusX: Int, radiusY: Int, scales: FloatArray, wholeFrame: Boolean = false, pull: Float = 0f): Match? {
        var bestRank = -10f
        var bestM: Match? = null
        for (s in scales) {
            val sw = bw * s; val sh = bh * s
            val xs = if (wholeFrame) (sw / 2).roundToInt()..(g.w - sw / 2).roundToInt() else (x0 - radiusX).roundToInt()..(x0 + radiusX).roundToInt()
            val ys = if (wholeFrame) (sh / 2).roundToInt()..(g.h - sh / 2).roundToInt() else (y0 - radiusY).roundToInt()..(y0 + radiusY).roundToInt()
            for (y in ys step step) for (x in xs step step) {
                val patch = sample(g, x.toFloat(), y.toFloat(), sw, sh) ?: continue
                val score = ncc(patch)
                val dist = kotlin.math.hypot((x - x0) / g.w, (y - y0) / g.h)
                val rank = score - pull * dist
                if (bestM == null || rank > bestRank) { bestM = Match(x.toFloat(), y.toFloat(), s, score); bestRank = rank }
            }
        }
        return bestM
    }

    /** tw×th samples over the box centred at (x, y), zero-mean and unit-length; null if the box is mostly off-frame or flat. */
    private fun sample(g: Gray, x: Float, y: Float, bw: Float, bh: Float): FloatArray? {
        val left = x - bw / 2; val top = y - bh / 2
        if (x < 0 || y < 0 || x >= g.w || y >= g.h) return null
        val out = FloatArray(tw * th)
        var sum = 0f
        for (j in 0 until th) for (i in 0 until tw) {
            val v = g.at((left + (i + 0.5f) * bw / tw).toInt(), (top + (j + 0.5f) * bh / th).toInt()).toFloat()
            out[j * tw + i] = v; sum += v
        }
        val mean = sum / out.size
        var sq = 0f
        for (k in out.indices) { out[k] -= mean; sq += out[k] * out[k] }
        val norm = sqrt(sq)
        if (norm < 1e-3f * out.size) return null
        for (k in out.indices) out[k] /= norm
        return out
    }

    private fun ncc(patch: FloatArray): Float {
        var s = 0f
        for (k in patch.indices) s += patch[k] * tpl[k]
        return s
    }

    private companion object {
        const val MIN_SCORE = 0.55f
        /** A little lower for the first find, which crosses a few seconds and possibly a step closer or back. */
        const val START_SCORE = 0.45f
    }
}

/** Builds a [Gray] of [outW]×[outH] from a camera Y plane, turned upright by [rotation] (same turn as the detector's image). */
fun grayFromLuma(
    y: java.nio.ByteBuffer, rowStride: Int, pixStride: Int, srcW: Int, srcH: Int, rotation: Int,
    crop: android.graphics.Rect = android.graphics.Rect(0, 0, srcW, srcH), outW: Int = 96,
): Gray {
    // Only the visible part (crop), keeping its upright shape: 96 wide and as tall as that shape needs.
    val uprightAspect = if (rotation % 180 == 0) crop.width().toFloat() / crop.height() else crop.height().toFloat() / crop.width()
    val outH = (outW / uprightAspect).toInt().coerceIn(96, 240)
    val px = ByteArray(outW * outH)
    for (v in 0 until outH) for (u in 0 until outW) {
        val fx = (u + 0.5f) / outW; val fy = (v + 0.5f) / outH
        val (sx, sy) = when (rotation) {
            90 -> fy to 1f - fx
            180 -> 1f - fx to 1f - fy
            270 -> 1f - fy to fx
            else -> fx to fy
        }
        val x = min(srcW - 1, max(0, crop.left + (sx * crop.width()).toInt()))
        val yy = min(srcH - 1, max(0, crop.top + (sy * crop.height()).toInt()))
        px[v * outW + u] = y.get(yy * rowStride + x * pixStride)
    }
    return Gray(outW, outH, px)
}
