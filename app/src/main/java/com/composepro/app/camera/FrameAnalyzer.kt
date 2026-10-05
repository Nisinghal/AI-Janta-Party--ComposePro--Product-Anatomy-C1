package com.composepro.app.camera

import android.graphics.RectF
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/** One recognised thing. Box is a fraction of the upright 3:4 frame, so it maps straight onto the preview. */
data class Thing(val id: Int?, val box: RectF, val category: String?) {
    val cx get() = box.centerX()
    val cy get() = box.centerY()
    val area get() = box.width() * box.height()
}

/** Everything the tips need from one camera frame. Light values are 0–255 luma and chroma averages. */
data class FrameResult(
    val things: List<Thing>,
    val label: String?,
    val meanY: Float,
    val centerY: Float,
    val clipFrac: Float,
    val warmth: Float,
)

/**
 * Runs on-device: ML Kit object detection every frame it can (about 6 a second), image labelling
 * about once a second, and simple light measurements straight from the camera's pixels.
 */
class FrameAnalyzer(private val onResult: (FrameResult) -> Unit) : ImageAnalysis.Analyzer {
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build(),
    )
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build())
    private var lastRun = 0L
    private var frameCount = 0
    private var label: String? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = proxy.image
        if (media == null || now - lastRun < 150) { proxy.close(); return }
        lastRun = now

        val rotation = proxy.imageInfo.rotationDegrees
        val w = (if (rotation % 180 == 0) proxy.width else proxy.height).toFloat()
        val h = (if (rotation % 180 == 0) proxy.height else proxy.width).toFloat()
        val light = measureLight(proxy)
        val input = InputImage.fromMediaImage(media, rotation)

        val objects = detector.process(input)
        val labels: Task<List<ImageLabel>>? = if (frameCount++ % 6 == 0) labeler.process(input) else null
        Tasks.whenAllComplete(listOfNotNull(objects, labels)).addOnCompleteListener {
            try {
                if (labels != null && labels.isSuccessful) label = pickLabel(labels.result)
                val things = if (objects.isSuccessful) objects.result.mapNotNull { it.toThing(w, h) }.sortedByDescending { it.area } else emptyList()
                onResult(FrameResult(things, label, light.meanY, light.centerY, light.clipFrac, light.warmth))
            } finally {
                proxy.close()
            }
        }
    }

    fun close() { detector.close(); labeler.close() }

    private fun DetectedObject.toThing(w: Float, h: Float): Thing? {
        val b = RectF(boundingBox.left / w, boundingBox.top / h, boundingBox.right / w, boundingBox.bottom / h)
        if (b.width() * b.height() < 0.02f) return null   // ignore specks
        return Thing(trackingId, b, labels.maxByOrNull { it.confidence }?.text)
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

    private val foodWords = setOf(
        "food", "dish", "cuisine", "dessert", "cake", "bread", "pizza", "salad", "soup", "noodle", "rice", "coffee", "tea",
        "drink", "cup", "juice", "cocktail", "wine", "beer", "fruit", "vegetable", "sushi", "meat", "breakfast", "lunch",
        "dinner", "snack", "cookie", "ice cream", "pasta", "sandwich", "burger", "egg", "chocolate", "pie", "tableware", "plate", "bowl",
    )

    /** Prefer a food word if one is confident enough; otherwise the most confident label of any kind (scope is any object). */
    private fun pickLabel(labels: List<ImageLabel>): String? {
        val sorted = labels.sortedByDescending { it.confidence }
        return (sorted.firstOrNull { it.text.lowercase() in foodWords } ?: sorted.firstOrNull())?.text
    }
}
