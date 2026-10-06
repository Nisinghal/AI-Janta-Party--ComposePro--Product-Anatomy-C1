package com.composepro.app.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.composepro.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/** One step the person can take right now, with the photographer's reason. */
data class Move(val action: String, val why: String)

/** What the photographer says about one frame. */
data class Advice(val seen: String, val subject: String, val ready: Boolean, val moves: List<Move>)

sealed interface AskResult {
    data class Ok(val advice: Advice) : AskResult
    data class Failed(val message: String) : AskResult
}

/**
 * "Ask photographer": sends one camera frame to Google Gemini, only when the person taps the button,
 * and gets back a photographer's read of it (user decisions, 2026-10-06: hybrid of live on-phone tips +
 * an on-demand photographer; Gemini's free tier because buying Claude credit failed with Indian cards).
 * Nothing is sent otherwise. Plain HTTPS + Android's built-in JSON, so no extra library.
 */
object Photographer {
    /**
     * Tried in order. The newest Flash is often "experiencing high demand" (503) for free-plan users
     * (seen 2026-10-06), so a busy, missing or rate-limited model falls through to the next one.
     * The "-latest" aliases move forward with each Google release.
     */
    private val MODELS = listOf("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash")
    private fun endpoint(model: String) = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    private val SYSTEM = """
        You are a friendly professional photographer standing next to an everyday person who is about to take a photo
        with their phone. You see exactly what their camera sees. They are not photographers: use plain words, no jargon
        (no "rule of thirds", "negative space", "bokeh", "exposure").

        Look at the frame like a photographer: what the photo is about, the background (mess, distractions, things cut
        off at the edges), the light (direction, harshness, colour, shadows on the subject), the angle and height of the
        phone, how much space is around the subject, whether lines are straight, and whether anything could be moved or
        removed to make it cleaner.

        Give only moves they can do in the next 10 seconds: move or tilt the phone, step closer or back, change height,
        turn toward or away from the light, move or remove small things in front of them, change what is behind the
        subject. Never suggest editing apps, filters, buying gear or changing camera settings.
        Be specific to THIS frame: name the real things you see ("the blue bowl", "the bedsheet behind the vase").
        Give 2 or 3 moves, most important first; each action at most 12 words, each reason at most 15 words.
        If the frame is already good, say so, set ready to true and give no moves.
    """.trimIndent()

    /** The answer's shape, so Gemini replies with JSON the app can read every time. */
    private val SCHEMA = JSONObject(
        """
        {"type":"OBJECT","properties":{
          "seen":{"type":"STRING","description":"What is in the frame, in one plain sentence (max 20 words)"},
          "subject":{"type":"STRING","description":"The subject the photo should be about, in a few words"},
          "ready":{"type":"BOOLEAN","description":"True only if the frame is already a good photo and needs no moves"},
          "moves":{"type":"ARRAY","description":"2 or 3 moves, most important first; empty if ready","items":{
            "type":"OBJECT","properties":{
              "action":{"type":"STRING","description":"The move as a short instruction, max 12 words"},
              "why":{"type":"STRING","description":"Why it makes the photo better, max 15 words"}},
            "required":["action","why"]}}},
         "required":["seen","subject","ready","moves"]}
        """,
    )

    val hasKey get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /**
     * Blocking: call from a background thread. [context] is what the phone already knows
     * (angle, zoom, what the on-phone detector saw) so the advice fits how it's being held.
     */
    fun ask(frame: Bitmap, context: String): AskResult {
        if (!hasKey) return AskResult.Failed("The photographer needs a Gemini API key. Add it to local.properties and rebuild the app.")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", toJpegBase64(frame))))
                            .put(JSONObject().put("text", context)),
                    ),
                ),
            )
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("responseSchema", SCHEMA))

        var last: AskResult = AskResult.Failed("The photographer is busy right now. Try again in a moment.")
        for (model in MODELS) {
            val (code, text) = try {
                post(model, body.toString())
            } catch (e: SocketTimeoutException) {
                return AskResult.Failed("The photographer took too long. Try again.")
            } catch (e: IOException) {
                return AskResult.Failed("Couldn't reach the photographer. Check your internet and try again.")
            }
            if (code in 200..299) {
                val advice = parse(text)
                Log.i("ComposePro", "Gemini $model answered: ${advice ?: text.take(400)}")
                return advice?.let { AskResult.Ok(it) } ?: AskResult.Failed("The photographer couldn't answer this time. Try again.")
            }
            Log.w("ComposePro", "Gemini $model error $code: ${text.take(300)}")
            when {
                code == 400 && "API_KEY_INVALID" in text -> return AskResult.Failed("The Gemini API key isn't working. Check it in local.properties.")
                code == 403 -> return AskResult.Failed("The Gemini API key isn't allowed to do this. Check it in Google AI Studio.")
                // Busy, gone, or this model's free limit used up: try the next model.
                code == 429 -> last = AskResult.Failed("Too many asks for the free plan right now. Wait a minute and try again.")
                code >= 500 || code == 404 -> last = AskResult.Failed("The photographer is busy right now. Try again in a moment.")
                else -> return AskResult.Failed("The photographer had a problem ($code). Try again.")
            }
        }
        return last
    }

    /** One POST; returns the status code and the body (the error body when it failed). */
    private fun post(model: String, json: String): Pair<Int, String> {
        val conn = (URL(endpoint(model)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
        }
        try {
            conn.outputStream.use { it.write(json.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    /** Gemini puts the JSON answer as text in the first candidate. Null if it was blocked or isn't valid. */
    private fun parse(reply: String): Advice? = try {
        val text = JSONObject(reply).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        val o = JSONObject(text)
        val moves = o.optJSONArray("moves") ?: JSONArray()
        Advice(
            seen = o.optString("seen"),
            subject = o.optString("subject"),
            ready = o.optBoolean("ready"),
            moves = (0 until moves.length()).map { i ->
                val m = moves.getJSONObject(i)
                Move(m.optString("action"), m.optString("why"))
            }.filter { it.action.isNotBlank() },
        )
    } catch (e: Exception) {
        null
    }

    /** About 1000px on the long side is plenty to judge a composition, and keeps the upload small and quick. */
    private fun toJpegBase64(src: Bitmap): String {
        val scale = minOf(1f, 1024f / max(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt(), true) else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
