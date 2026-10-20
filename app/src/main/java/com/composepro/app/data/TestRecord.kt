package com.composepro.app.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.composepro.app.BuildConfig
import com.composepro.app.ai.Advice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Phase 0 of PLAN.md (2026-10-20): a before/after baseline. In the owner's own build only (never the tester APK), every
 * set of steps is saved with a small copy of the camera view it was given for, in the app's private folder on the
 * phone. Pulled to the laptop over the cable afterwards (adb), so scenes away from the laptop (a scooter outside)
 * still count. Nothing leaves the phone.
 */
object TestRecord {
    val enabled get() = BuildConfig.BUILD_TYPE == "debug"

    private fun dir(context: Context) = File(context.filesDir, "test-record").apply { mkdirs() }

    fun save(context: Context, view: Bitmap, advice: Advice, zoom: Float, tiltDeg: Float) {
        if (!enabled) return
        try {
            val t = System.currentTimeMillis()
            val scale = 540f / maxOf(view.width, view.height)
            val small = Bitmap.createScaledBitmap(view, (view.width * scale).toInt().coerceAtLeast(1), (view.height * scale).toInt().coerceAtLeast(1), true)
            File(dir(context), "$t.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            val line = JSONObject()
                .put("t", t)
                .put("image", "$t.jpg")
                .put("seen", advice.seen)
                .put("subject", advice.subject)
                .put("kind", advice.kind.name)
                .put("frame", advice.frame?.label ?: "")
                .put("ready", advice.ready)
                .put("zoom", zoom.toDouble())
                .put("tilt", tiltDeg.toDouble())
                .put("box", advice.subjectBox?.let { JSONArray().put(it.left.toDouble()).put(it.top.toDouble()).put(it.right.toDouble()).put(it.bottom.toDouble()) })
                .put("steps", JSONArray().apply {
                    advice.moves.forEach { m ->
                        put(JSONObject().put("action", m.action).put("why", m.why).put("check", m.check.name)
                            .put("target", if (m.targetX != null) JSONArray().put(m.targetX.toDouble()).put((m.targetY ?: 0f).toDouble()) else null)
                            .put("size", m.size?.toDouble()).put("zoom", m.zoom?.toDouble()).put("angle", m.angle?.name))
                    }
                })
            File(dir(context), "record.jsonl").appendText(line.toString() + "\n")
        } catch (e: Exception) {
            Log.w("ComposePro", "Test record not saved", e)
        }
    }
}
