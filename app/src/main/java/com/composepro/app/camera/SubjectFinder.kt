package com.composepro.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the main subject(s) in view, whatever they are: a cup, a statue, a scooter, a plate of food. No list of object
 * types (PLAN.md Phase 1; user decision 2026-10-20: "there are so many objects, we can't add each; I want the camera
 * to detect"). Google ML Kit's subject segmentation, on the phone; its model is installed once by Play services.
 *
 * Runs about twice a second on its own thread and keeps the latest answer; the camera never waits for it.
 */
class SubjectFinder(context: Context) {
    private val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask().build())
            .setExecutor(Executors.newSingleThreadExecutor())
            .build(),
    )
    @Volatile private var busy = false
    @Volatile private var lastRun = 0L
    private var logged = 0

    /** The latest subjects as fractions of the view, biggest first, and when they were found. */
    @Volatile var subjects: List<RectF> = emptyList()
        private set
    @Volatile var foundAt = 0L
        private set

    init {
        // Ask Play services to fetch the model now if it doesn't have it yet (only the first time, ~ a few MB).
        ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build())
            .addOnFailureListener { Log.w(TAG, "Subject model install failed: ${it.message}") }
    }

    /** Hands over an upright copy of what's on screen; ignored while the last one is still being looked at. */
    fun offer(view: Bitmap) {
        val now = SystemClock.elapsedRealtime()
        if (busy || now - lastRun < EVERY_MS) return
        busy = true
        lastRun = now
        val w = view.width.toFloat(); val h = view.height.toFloat()
        segmenter.process(InputImage.fromBitmap(view, 0))
            .addOnSuccessListener { r ->
                subjects = r.subjects.map { s ->
                    RectF(s.startX / w, s.startY / h, min(1f, (s.startX + s.width) / w), min(1f, (s.startY + s.height) / h))
                }.filter { it.width() * it.height() > 0.004f }
                    .sortedByDescending { it.width() * it.height() }
                foundAt = SystemClock.elapsedRealtime()
                if (logged < 5) {
                    logged++
                    Log.i(TAG, "Subjects in ${foundAt - now} ms: ${subjects.map { it.toShortString() }}")
                }
            }
            .addOnFailureListener { e -> if (logged < 5) { logged++; Log.w(TAG, "Subject finder not ready: ${e.message}") } }
            .addOnCompleteListener { busy = false }
    }

    fun close() = segmenter.close()

    companion object {
        private const val TAG = "ComposePro"
        private const val EVERY_MS = 450L

        /**
         * The main subject: the biggest one, together with any others nearly as big (a plate and its glass, three
         * dishes), as one box. Small bits (a spoon, a crumb) don't pull the frame around.
         */
        fun main(subjects: List<RectF>): RectF? {
            val first = subjects.firstOrNull() ?: return null
            val big = first.width() * first.height()
            val box = RectF(first)
            subjects.drop(1).filter { it.width() * it.height() >= big * 0.35f }.forEach { box.union(it) }
            return box
        }

        /** The subject under a tap (fractions), or the nearest one to it. */
        fun at(subjects: List<RectF>, x: Float, y: Float): RectF? =
            subjects.firstOrNull { it.contains(x, y) } ?: subjects.minByOrNull {
                val dx = max(0f, max(it.left - x, x - it.right)); val dy = max(0f, max(it.top - y, y - it.bottom))
                dx * dx + dy * dy
            }
    }
}
