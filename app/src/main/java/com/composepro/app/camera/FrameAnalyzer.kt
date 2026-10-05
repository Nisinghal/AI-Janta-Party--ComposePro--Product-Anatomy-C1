package com.composepro.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import kotlin.math.max
import kotlin.math.min

/** One recognised thing. Box is a fraction of the upright 3:4 frame, so it maps straight onto the preview. Name is what the model calls it ("cup", "bowl", "laptop"). */
data class Thing(val id: Int?, val box: RectF, val category: String?) {
    val cx get() = box.centerX()
    val cy get() = box.centerY()
    val area get() = box.width() * box.height()
}

/** Everything the tips need from one camera frame. Light values are 0-255 luma and chroma averages. */
data class FrameResult(
    val things: List<Thing>,
    val meanY: Float,
    val centerY: Float,
    val clipFrac: Float,
    val warmth: Float,
)

/**
 * Runs on-device: Google's EfficientDet-Lite0 model (bundled in the app, 80 everyday object types)
 * about 8 times a second, plus simple light measurements straight from the camera's pixels.
 */
class FrameAnalyzer(private val context: Context, private val onResult: (FrameResult) -> Unit) : ImageAnalysis.Analyzer {
    private val mainThread = ContextCompat.getMainExecutor(context)
    private var detector: ObjectDetector? = null
    private var lastRun = 0L

    override fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRun < 120) { proxy.close(); return }
        lastRun = now
        try {
            val light = measureLight(proxy)
            val image = upright(proxy.toBitmap(), proxy.imageInfo.rotationDegrees)
            val things = steady(detect(image), now)
            Log.d(TAG, "things=${things.size} ${things.map { it.category }}")
            val r = FrameResult(things, light.meanY, light.centerY, light.clipFrac, light.warmth)
            mainThread.execute { onResult(r) }
        } catch (e: Exception) {
            Log.e(TAG, "analysis failed", e)
        } finally {
            proxy.close()
        }
    }

    fun close() { detector?.close(); detector = null }

    private fun detectorOrNull(): ObjectDetector? = detector ?: try {
        ObjectDetector.createFromOptions(
            context,
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(8)
                .setScoreThreshold(0.3f)
                .build(),
        ).also { detector = it }
    } catch (e: Exception) {
        Log.e(TAG, "couldn't load the detection model", e)
        null
    }

    /** Small and upright, so boxes come back in the same orientation as the preview. */
    private fun upright(src: Bitmap, rotation: Int): Bitmap {
        val scale = min(1f, 480f / max(src.width, src.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private class Found(val box: RectF, val name: String, val score: Float)

    private fun detect(image: Bitmap): List<Found> {
        val d = detectorOrNull() ?: return emptyList()
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        val all = d.detect(BitmapImageBuilder(image).build()).detections().mapNotNull { det ->
            val c = det.categories().firstOrNull() ?: return@mapNotNull null
            if (c.categoryName() in ignored) return@mapNotNull null
            val b = det.boundingBox()
            val box = RectF((b.left / w).coerceIn(0f, 1f), (b.top / h).coerceIn(0f, 1f), (b.right / w).coerceIn(0f, 1f), (b.bottom / h).coerceIn(0f, 1f))
            if (box.width() * box.height() < 0.01f) null else Found(box, friendly(c.categoryName()), c.score())
        }.sortedByDescending { it.score }
        // One plate can come back as both "bowl" and "pizza": keep only the surer one.
        val kept = mutableListOf<Found>()
        for (f in all) if (kept.none { iou(it.box, f.box) > 0.5f }) kept += f
        return kept
    }

    /** Gives each thing a lasting id by overlap with last frame's things, and keeps one the model missed for up to 0.6s, so marks and arrows don't flicker. */
    private val recent = mutableMapOf<Int, Pair<Thing, Long>>()
    private var nextId = 1

    private fun steady(found: List<Found>, now: Long): List<Thing> {
        val used = mutableSetOf<Int>()
        val fresh = found.map { f ->
            val match = recent.entries.filter { it.key !in used }.maxByOrNull { iou(it.value.first.box, f.box) }
                ?.takeIf { iou(it.value.first.box, f.box) > 0.3f }
            val id = match?.key ?: nextId++
            used += id
            Thing(id, f.box, f.name)
        }
        fresh.forEach { recent[it.id!!] = it to now }
        recent.entries.removeAll { now - it.value.second > 600 }
        val held = recent.filterKeys { it !in used }.values.map { it.first }
        return (fresh + held).sortedByDescending { it.area }.take(5)
    }

    private class Light(val meanY: Float, val centerY: Float, val clipFrac: Float, val warmth: Float)

    /** Samples a grid of pixels: overall brightness, brightness and blown-out share in the middle, and warm colour cast. */
    private fun measureLight(proxy: ImageProxy): Light {
        val yPlane = proxy.planes[0]
        val yBuf = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixStride = yPlane.pixelStride
        val width = proxy.width
        val height = proxy.height
        var sum = 0L; var n = 0
        var cSum = 0L; var cN = 0; var clipped = 0
        val step = 8
        var y = 0
        while (y < height) {
            var x = 0
            val inMidRow = y > height / 4 && y < height * 3 / 4
            while (x < width) {
                val v = yBuf.get(y * rowStride + x * pixStride).toInt() and 0xFF
                sum += v; n++
                if (inMidRow && x > width / 4 && x < width * 3 / 4) { cSum += v; cN++; if (v >= 248) clipped++ }
                x += step
            }
            y += step
        }
        val uPlane = proxy.planes[1]; val vPlane = proxy.planes[2]
        val uBuf = uPlane.buffer; val vBuf = vPlane.buffer
        var uSum = 0L; var vSum = 0L; var m = 0
        var cy = 0
        while (cy < height / 2) {
            var cx = 0
            while (cx < width / 2) {
                val ui = cy * uPlane.rowStride + cx * uPlane.pixelStride
                val vi = cy * vPlane.rowStride + cx * vPlane.pixelStride
                if (ui < uBuf.limit() && vi < vBuf.limit()) {
                    uSum += uBuf.get(ui).toInt() and 0xFF
                    vSum += vBuf.get(vi).toInt() and 0xFF
                    m++
                }
                cx += step
            }
            cy += step
        }
        val meanU = if (m > 0) uSum.toFloat() / m else 128f
        val meanV = if (m > 0) vSum.toFloat() / m else 128f
        return Light(
            meanY = if (n > 0) sum.toFloat() / n else 0f,
            centerY = if (cN > 0) cSum.toFloat() / cN else 0f,
            clipFrac = if (cN > 0) clipped.toFloat() / cN else 0f,
            warmth = (meanV - 128f) - (meanU - 128f),   // high = yellow/orange cast
        )
    }

    private companion object {
        const val TAG = "ComposePro"

        /** Tips are for things, not people (BRIEF.md); a table is the background, not a thing to arrange. */
        val ignored = setOf("person", "dining table", "bed", "couch")

        fun friendly(name: String) = when (name) {
            "cell phone" -> "phone"
            "potted plant" -> "plant"
            "tv" -> "TV"
            else -> name
        }

        fun iou(a: RectF, b: RectF): Float {
            val iw = min(a.right, b.right) - max(a.left, b.left)
            val ih = min(a.bottom, b.bottom) - max(a.top, b.top)
            if (iw <= 0 || ih <= 0) return 0f
            val inter = iw * ih
            return inter / (a.width() * a.height() + b.width() * b.height() - inter)
        }
    }
}
