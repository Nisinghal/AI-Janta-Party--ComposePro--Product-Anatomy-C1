package com.composepro.app.ai

import android.graphics.Bitmap
import android.graphics.RectF
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
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/** How a move can be checked: by the phone's tilt sensor, by where the subject sits in the frame, or only by asking again. */
enum class CheckBy { Angle, Frame, Other }

/** The phone angle a move asks for. */
enum class ShotAngle { Above, Diner, Eye }

/**
 * One step the person can take right now, with the photographer's reason, and how the phone can tell it's done.
 * For [CheckBy.Frame]: the subject's centre should end up at ([targetX], [targetY]) (fractions of the frame),
 * filling about [size] of the frame's height (null = any size).
 */
data class Move(
    val action: String,
    val why: String,
    val check: CheckBy = CheckBy.Other,
    val angle: ShotAngle? = null,
    val targetX: Float? = null,
    val targetY: Float? = null,
    val size: Float? = null,
)

/** What the photographer says about one frame. [subjectBox] is where the subject is now (fractions of the frame), if it said. */
data class Advice(val seen: String, val subject: String, val ready: Boolean, val moves: List<Move>, val subjectBox: RectF?)

/** The photographer's second look: which moves are done, a short note for each, and whether it's ready. */
data class Review(val done: List<Boolean>, val notes: List<String>, val ready: Boolean, val next: String)

sealed interface Reply<out T> {
    data class Ok<T>(val value: T) : Reply<T>
    data class Failed(val message: String) : Reply<Nothing>
}

/**
 * "Ask photographer": sends one camera frame to Google Gemini, only when the person taps, and gets back a
 * photographer's plan; "Check my shot" sends one more frame to check it (user decisions, 2026-10-06: hybrid of
 * on-phone tips + an on-demand photographer; Gemini's free tier because buying Claude credit failed with Indian cards;
 * the photographer's steps must be checkable). Nothing is sent otherwise. Plain HTTPS + Android's built-in JSON.
 */
object Photographer {
    /**
     * Raced: the same request goes to all of these at once and the first good answer wins. On the free plan one
     * model can sit in Google's queue for 20+ s (gemini-3.5-flash-lite took 22–23 s per answer, phone log 2026-10-06)
     * while another answers in a few; each model has its own free limit, so racing stays free.
     * The "-latest" alias moves forward with each Google release; the dated ones are older, often quieter models.
     */
    @Volatile private var MODELS = listOf("gemini-flash-lite-latest", "gemini-flash-latest")
    @Volatile private var warmed = false

    /**
     * Call when the camera opens. Opens the connection to Google early (the first request on mobile data spent
     * ~7.5 s just connecting; later ones ~1.5 s) and asks which models this key can use, since older ones get
     * retired (gemini-2.5/2.0-flash-lite answered 404, 2026-10-06). Up to 3 Flash models are then raced.
     */
    fun warmUp() {
        if (!hasKey || warmed) return
        warmed = true
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
        }
        try {
            if (conn.responseCode !in 200..299) return
            val list = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("models") ?: return
            val skip = listOf("image", "tts", "live", "audio", "embedding", "exp", "robotics", "computer")
            val usable = (0 until list.length()).map { list.getJSONObject(it) }.filter { m ->
                val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                val name = m.optString("name").removePrefix("models/")
                "generateContent" in methods && "flash" in name && skip.none { it in name }
            }.map { it.optString("name").removePrefix("models/") }
            // Lite first (quickest), then the newest version number.
            fun version(n: String) = Regex("""\d+(\.\d+)?""").find(n)?.value?.toFloatOrNull() ?: 0f
            val picked = usable.filter { "latest" !in it }.sortedWith(compareBy<String>({ "lite" !in it }, { -version(it) })).take(2)
            MODELS = (listOf("gemini-flash-lite-latest") + picked).distinct().take(3)
            Log.i("ComposePro", "Gemini models to race: $MODELS (of ${usable.size} usable)")
        } catch (e: Exception) {
            Log.w("ComposePro", "Gemini warm-up failed: ${e.message}")
        }
        // No disconnect(): the reply was read in full, so the connection goes back to Android's pool for the ask.
    }

    /**
     * Gemini's newer models quietly "think" before answering, which adds seconds. Two or three photo steps don't need
     * much, so each model gets the lightest setting it accepts. Models differ ("thinkingBudget: 0" was rejected with a
     * bare "invalid argument"), so on a 400 the next option is tried and the one that works is kept for that model.
     */
    private val THINKING = listOf(
        JSONObject().put("thinkingLevel", "minimal"),
        JSONObject().put("thinkingBudget", 0),
        JSONObject().put("thinkingLevel", "low"),
        null,
    )
    private val thinkingFor = ConcurrentHashMap<String, Int>()
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "gemini").apply { isDaemon = true } }
    private fun endpoint(model: String) = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    private val PERSONA = """
        You are a friendly professional photographer standing next to an everyday person who is about to take a photo
        with their phone. You see exactly what their camera sees. They are not photographers: use plain words, no jargon
        (no "rule of thirds", "negative space", "bokeh", "exposure").
    """.trimIndent()

    private val ASK_SYSTEM = PERSONA + "\n\n" + """
        Look at the frame like a photographer: what the photo is about, the background (mess, distractions, things cut
        off at the edges), the light (direction, harshness, colour, shadows on the subject), the angle and height of the
        phone, how much space is around the subject, whether lines are straight, and whether anything could be moved or
        removed to make it cleaner.

        Give only moves they can do in the next 10 seconds: move or tilt the phone, step closer or back, change height,
        turn toward or away from the light, move or remove small things in front of them, change what is behind the
        subject. Never suggest editing apps, filters, buying gear, changing camera settings or tapping the screen.
        The frame may be a little blurred because the phone is moving while they aim; ignore that kind of blur.
        Only mention people if you can clearly see a person.
        Be specific to THIS frame: name the real things you see ("the blue bowl", "the bedsheet behind the vase").
        Give 2 or 3 moves, most important first; each action at most 12 words, each reason at most 15 words.
        One idea per move, so each can be checked on its own.

        For each move say how it can be checked:
        - check "angle" if the move is only about the phone's height or tilt; set angle to "above" (phone flat, looking
          straight down), "diner" (tilted, like sitting at a table) or "eye" (phone upright at the subject's height).
        - check "frame" if the move is about where the subject sits in the picture or how big it is; set target_x and
          target_y to where the centre of the subject should end up (0 = left/top, 1 = right/bottom) and size to how
          much of the picture's height it should fill (0 to 1).
        - check "other" for everything else (light, background, moving or removing things).
        Also give subject_box: where the subject is now, as [ymin, xmin, ymax, xmax] from 0 to 1000.
        If the frame is already good, say so, set ready to true and give no moves.
    """.trimIndent()

    private val CHECK_SYSTEM = PERSONA + "\n\n" + """
        A moment ago you gave this person some moves. Now look at the new frame and judge each move honestly:
        done is true only if the frame clearly shows it was done. For each move write a note of at most 10 words:
        "Done" if done, otherwise exactly what is still off ("Almost, lower the phone a little more").
        Set ready to true only if the photo is now good to take. In next, say the single most useful thing to do now,
        at most 12 words (or "Take the photo." if ready).
    """.trimIndent()

    private val ASK_SCHEMA = JSONObject(
        """
        {"type":"OBJECT","properties":{
          "seen":{"type":"STRING","description":"What is in the frame, in one plain sentence (max 20 words)"},
          "subject":{"type":"STRING","description":"The subject the photo should be about, in a few words"},
          "ready":{"type":"BOOLEAN","description":"True only if the frame is already a good photo and needs no moves"},
          "subject_box":{"type":"ARRAY","items":{"type":"INTEGER"},"description":"Where the subject is now: [ymin, xmin, ymax, xmax], 0 to 1000"},
          "moves":{"type":"ARRAY","description":"2 or 3 moves, most important first; empty if ready","items":{
            "type":"OBJECT","properties":{
              "action":{"type":"STRING","description":"The move as a short instruction, max 12 words"},
              "why":{"type":"STRING","description":"Why it makes the photo better, max 15 words"},
              "check":{"type":"STRING","enum":["angle","frame","other"]},
              "angle":{"type":"STRING","enum":["above","diner","eye"],"description":"Only when check is angle"},
              "target_x":{"type":"NUMBER","description":"Only when check is frame: where the subject's centre should be, 0 left to 1 right"},
              "target_y":{"type":"NUMBER","description":"Only when check is frame: 0 top to 1 bottom"},
              "size":{"type":"NUMBER","description":"Only when check is frame: share of the picture's height the subject should fill"}},
            "required":["action","why","check"]}}},
         "required":["seen","subject","ready","moves"]}
        """,
    )

    private val CHECK_SCHEMA = JSONObject(
        """
        {"type":"OBJECT","properties":{
          "moves":{"type":"ARRAY","description":"One entry per move, in the same order","items":{
            "type":"OBJECT","properties":{
              "done":{"type":"BOOLEAN"},
              "note":{"type":"STRING","description":"Done, or what is still off, max 10 words"}},
            "required":["done","note"]}},
          "ready":{"type":"BOOLEAN"},
          "next":{"type":"STRING","description":"The single most useful thing to do now, max 12 words"}},
         "required":["moves","ready","next"]}
        """,
    )

    val hasKey get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /** Blocking: call from a background thread. [context] is what the phone knows (angle, zoom, what its detector saw). */
    fun ask(frame: Bitmap, context: String): Reply<Advice> =
        when (val r = generate(ASK_SYSTEM, ASK_SCHEMA, frame, context)) {
            is Reply.Failed -> r
            is Reply.Ok -> parseAdvice(r.value)?.let { Reply.Ok(it) } ?: Reply.Failed("The photographer couldn't answer this time. Try again.")
        }

    /** Blocking: sends the new frame and the moves given earlier, and gets back which are done. */
    fun check(frame: Bitmap, advice: Advice, context: String): Reply<Review> {
        val list = advice.moves.mapIndexed { i, m -> "${i + 1}. ${m.action}" }.joinToString("\n")
        val text = "The subject: ${advice.subject}.\nThe moves you gave:\n$list\n$context"
        return when (val r = generate(CHECK_SYSTEM, CHECK_SCHEMA, frame, text)) {
            is Reply.Failed -> r
            is Reply.Ok -> parseReview(r.value, advice.moves.size)?.let { Reply.Ok(it) } ?: Reply.Failed("The photographer couldn't check this time. Try again.")
        }
    }

    /** Sends one frame + text to every model at once and returns the first good answer (its JSON text). */
    private fun generate(system: String, schema: JSONObject, frame: Bitmap, text: String): Reply<String> {
        if (!hasKey) return Reply.Failed("The photographer needs a Gemini API key. Add it to local.properties and rebuild the app.")
        val image = toJpegBase64(frame)
        val started = System.currentTimeMillis()
        val done = ExecutorCompletionService<Reply<String>>(pool)
        MODELS.forEach { model -> done.submit(Callable { askOne(model, system, schema, image, text, started) }) }
        var failure: Reply<String> = Reply.Failed("The photographer is busy right now. Try again in a moment.")
        repeat(MODELS.size) {
            val r = try { done.poll(60, TimeUnit.SECONDS)?.get() } catch (e: Exception) { null }
                ?: return Reply.Failed("The photographer took too long. Try again.")
            if (r is Reply.Ok) return r   // the slower ones finish in the background and are ignored
            if (r is Reply.Failed && (it == 0 || r.isKeyProblem())) failure = r
        }
        return failure
    }

    private fun Reply<String>.isKeyProblem() = this is Reply.Failed && ("API key" in message)

    /** One model: post, stepping through thinking settings it refuses. Never throws. */
    private fun askOne(model: String, system: String, schema: JSONObject, image: String, text: String, started: Long): Reply<String> {
        fun body(): String = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", image)))
                            .put(JSONObject().put("text", text)),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema).apply {
                    THINKING[thinkingFor[model] ?: 0]?.let { put("thinkingConfig", it) }
                },
            )
            .toString()
        return try {
            var (code, reply) = post(model, body())
            // A bare "invalid argument" (not a bad key) means this model won't take the thinking setting: try the next.
            while (code == 400 && "API_KEY_INVALID" !in reply && (thinkingFor[model] ?: 0) < THINKING.lastIndex) {
                thinkingFor[model] = (thinkingFor[model] ?: 0) + 1
                val retry = post(model, body())
                code = retry.first; reply = retry.second
            }
            val ms = System.currentTimeMillis() - started
            if (code in 200..299) {
                val answer = answerText(reply)
                val version = try { JSONObject(reply).optString("modelVersion") } catch (e: Exception) { "" }
                Log.i("ComposePro", "Gemini $model ($version, thinking ${THINKING[thinkingFor[model] ?: 0] ?: "default"}) answered in $ms ms: ${answer ?: reply.take(300)}")
                answer?.let { Reply.Ok(it) } ?: Reply.Failed("The photographer couldn't answer this time. Try again.")
            } else {
                Log.w("ComposePro", "Gemini $model error $code after $ms ms: ${reply.take(200)}")
                when {
                    code == 400 && "API_KEY_INVALID" in reply -> Reply.Failed("The Gemini API key isn't working. Check it in local.properties.")
                    code == 403 -> Reply.Failed("The Gemini API key isn't allowed to do this. Check it in Google AI Studio.")
                    code == 429 -> Reply.Failed("Too many asks for the free plan right now. Wait a minute and try again.")
                    else -> Reply.Failed("The photographer is busy right now. Try again in a moment.")
                }
            }
        } catch (e: SocketTimeoutException) {
            Reply.Failed("The photographer took too long. Try again.")
        } catch (e: IOException) {
            Reply.Failed("Couldn't reach the photographer. Check your internet and try again.")
        }
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
        // Reading the reply in full (and not calling disconnect()) lets Android reuse the connection next time.
        conn.outputStream.use { it.write(json.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    }

    /** Gemini puts the JSON answer as text in the first candidate. Null if it was blocked. */
    private fun answerText(reply: String): String? = try {
        JSONObject(reply).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    } catch (e: Exception) {
        null
    }

    private fun parseAdvice(text: String): Advice? = try {
        val o = JSONObject(text)
        val moves = o.optJSONArray("moves") ?: JSONArray()
        val box = o.optJSONArray("subject_box")?.takeIf { it.length() == 4 }?.let { b ->
            // Gemini's own box order: [ymin, xmin, ymax, xmax] on a 0–1000 scale.
            RectF(b.getDouble(1).toFloat() / 1000f, b.getDouble(0).toFloat() / 1000f, b.getDouble(3).toFloat() / 1000f, b.getDouble(2).toFloat() / 1000f)
        }
        Advice(
            seen = o.optString("seen"),
            subject = o.optString("subject"),
            ready = o.optBoolean("ready"),
            moves = (0 until moves.length()).map { i ->
                val m = moves.getJSONObject(i)
                val check = when (m.optString("check")) { "angle" -> CheckBy.Angle; "frame" -> CheckBy.Frame; else -> CheckBy.Other }
                val angle = when (m.optString("angle")) { "above" -> ShotAngle.Above; "diner" -> ShotAngle.Diner; "eye" -> ShotAngle.Eye; else -> null }
                fun frac(key: String) = if (m.has(key)) m.optDouble(key).toFloat().takeIf { it in 0f..1f } else null
                val tx = frac("target_x"); val ty = frac("target_y")
                Move(
                    action = m.optString("action"),
                    why = m.optString("why"),
                    // A check the phone can't actually do falls back to "ask again".
                    check = when {
                        check == CheckBy.Angle && angle == null -> CheckBy.Other
                        check == CheckBy.Frame && (tx == null || ty == null) -> CheckBy.Other
                        else -> check
                    },
                    angle = angle, targetX = tx, targetY = ty, size = frac("size"),
                )
            }.filter { it.action.isNotBlank() }.take(3),
            subjectBox = box,
        )
    } catch (e: Exception) {
        null
    }

    private fun parseReview(text: String, count: Int): Review? = try {
        val o = JSONObject(text)
        val arr = o.optJSONArray("moves") ?: JSONArray()
        Review(
            done = List(count) { i -> arr.optJSONObject(i)?.optBoolean("done") ?: false },
            notes = List(count) { i -> arr.optJSONObject(i)?.optString("note").orEmpty() },
            ready = o.optBoolean("ready"),
            next = o.optString("next"),
        )
    } catch (e: Exception) {
        null
    }

    /** About 640px on the long side is plenty to judge a composition, and keeps the upload small and quick. */
    private fun toJpegBase64(src: Bitmap): String {
        val scale = minOf(1f, 640f / max(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt(), true) else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
