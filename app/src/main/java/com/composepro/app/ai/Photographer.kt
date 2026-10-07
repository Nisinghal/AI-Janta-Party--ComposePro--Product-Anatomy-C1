package com.composepro.app.ai

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Base64
import android.util.Log
import com.composepro.app.BuildConfig
import com.composepro.app.guide.Guide
import com.composepro.app.guide.targets
import kotlin.math.hypot
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
enum class CheckBy { Angle, Frame, Zoom, Other }

/** What sort of photo it is: things on a table that can be moved, a scene, or an animal/person. */
enum class ShotKind { TableTop, Scene, Living }

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
    /** For [CheckBy.Zoom]: the zoom level to tap (e.g. 2). */
    val zoom: Float? = null,
)

/** What the photographer says about one frame. [subjectBox] is where the subject is now (fractions of the frame), if it said. */
data class Advice(
    val seen: String,
    val subject: String,
    val ready: Boolean,
    val moves: List<Move>,
    val subjectBox: RectF?,
    /** The composition frame from the reference images that this shot is built on, drawn over the camera view. */
    val frame: Guide? = null,
    val frameWhy: String = "",
    val kind: ShotKind = ShotKind.TableTop,
)

/** How many spots each frame is laid out with, so the drawn spots and the photographer's targets are the same ones. */
fun frameSlots(g: Guide): Int = when (g) {
    Guide.Grid -> 4
    Guide.Circle -> 5
    Guide.Spiral -> 4
    else -> 3
}

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
        off at the edges), the light (direction, harshness, colour, shadows on the subject), the angle of the phone, how
        much space is around the subject, whether lines are straight, and whether anything could be moved or removed.

        First decide the kind of shot (set kind):
        - "tabletop": food, drinks or small things they can pick up. They can move things and the phone.
        - "scene": a tree, building, street, road, bridge, room, landscape or sky. Nothing can be moved; only the phone.
          Keep the whole main subject in, keep upright lines straight and the horizon level, leave some sky or ground
          around it. Never suggest filling the picture with ground, floor or sky.
        - "living": an animal or a person. Only the phone; keep their head in, leave space on the side they face.
        Pick ONE main subject (the thing the person is clearly pointing at) and build every move around it.

        Angle (the person's group found the old advice pushed "straight down" far too often):
        - Straight down ("above") ONLY for flat things seen best from the top: a pizza, a flat lay of several dishes, a
          book or a laptop keyboard, a plate whose food is flat.
        - 30 to 45 degrees ("diner") is the default for most dishes, drinks, cakes and objects: it shows top, side and depth.
        - Eye level ("eye") for tall things (bottles, glasses, burgers, vases, plants, watches standing up), animals, and
          most scenes.
        If the phone's current angle already suits the subject, do NOT give an angle move.

        Getting closer (moving in too far made photos distorted, shadowed by the phone and out of focus):
        - To make a small subject bigger, prefer zoom: a "zoom" move like "Tap 2× at the bottom-right" (only zoom levels
          this phone offers, listed below). Only ask to step closer if the phone is clearly far away.
        - Never ask for the subject to fill more than 60% of the picture's height; keep some space around it.
        - Keep the phone at least about 25 cm from small things, and watch for the phone's own shadow falling on them.

        Moves must be plain physical actions with the phone or the things in front of them: "step left", "step back",
        "crouch a little", "tilt the phone up", "tap 2×", "move the cup to the right", "turn so the window is beside you".
        Never ask them to line real things up with a drawn line ("align the street with the diagonal", "lead from corner
        to corner"); people can't map a 3D scene onto a 2D line.
        All moves must agree with each other: never include something in one move and remove it in another, never send
        the subject to two different spots. Only ask for possible moves: not below the table or ground the subject
        stands on, and if the phone is already close to the right height, don't ask again.
        Give only moves they can do in the next 10 seconds. Never suggest editing apps, filters, buying gear, changing
        camera settings (other than the zoom buttons) or tapping the screen. Ignore slight blur from a moving phone.
        Only mention people if you can clearly see a person. Name the real things you see ("the blue bowl").
        Give 2 or 3 moves, most important first; each action at most 12 words, each reason at most 15 words.
        One idea per move, so each can be checked on its own.

        Then choose ONE composition frame. Follow how the person is already framing: pick the frame that needs the
        smallest change, and the simplest one that works. Each frame has fixed spots, as (x, y) with 0 = left/top:
        - "thirds": cross points (0.33, 0.33), (0.67, 0.33), (0.33, 0.67), (0.67, 0.67). One subject a little off-centre.
        - "centre": one spot (0.5, 0.45). One strong, symmetrical or head-on subject (also roads and bridges seen
          straight down their length).
        Only for a TABLETOP with several things that can be arranged, also:
        - "front_back": big thing in front (0.42, 0.63), smaller thing behind (0.62, 0.36).
        - "diagonal": (0.29, 0.33), (0.5, 0.5), (0.71, 0.67). Two or three things in a line.
        - "triangle": (0.5, 0.3), (0.28, 0.68), (0.72, 0.68). Exactly three things.
        - "grid": (0.33, 0.35), (0.67, 0.35), (0.33, 0.63), (0.67, 0.63). Many of the same thing.
        - "circle": hero in the middle (0.5, 0.48), the rest around it.
        - "spiral": hero at (0.62, 0.40), others along the curve. A busy spread.
        A scene or a living subject always uses "thirds" or "centre".
        Set frame, and frame_why in at most 10 plain words.
        A "frame" move puts target_x/target_y on the frame's spot nearest to where the subject already is, and says it
        as a phone action ("Step right so the tree sits on the left grid line"). At most ONE "frame" move. A big subject
        that fills most of the picture moves sideways onto a third LINE, not its middle onto a cross point.

        For each move say how it can be checked:
        - check "angle" if the move is only about the phone's tilt; set angle to "above", "diner" or "eye".
        - check "zoom" if the move is to tap a zoom button; set zoom to that level (e.g. 2).
        - check "frame" if the move is about where the subject sits in the picture or how big it is. Set target_x,
          target_y (0 = left/top, 1 = right/bottom) and size (share of the picture's height, at most 0.6). The person
          sees a circle at that spot and a dot on the subject.
        - check "other" for everything else (light, background, moving or removing things, height).
        Always give subject_box: where the subject is now, as [ymin, xmin, ymax, xmax] from 0 to 1000, tight around it.
        If the frame is already good, say so, set ready to true and give no moves.
    """.trimIndent()

    private val CHECK_SYSTEM = PERSONA + "\n\n" + """
        A moment ago you gave this person some moves. Now look at the new frame and judge each move fairly, like a
        friendly photographer standing next to them: done is true if the move was done or nearly done, or if the frame
        now looks the way the move was aiming for. Moves you can't judge from one frame (phone height, distance) count
        as done when the result looks right. Moves marked (already done) stay done. For each move write a note of at most 10 words:
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
          "kind":{"type":"STRING","enum":["tabletop","scene","living"]},
          "frame":{"type":"STRING","enum":["thirds","centre","front_back","diagonal","triangle","grid","circle","spiral"]},
          "frame_why":{"type":"STRING","description":"Why this frame, max 10 plain words"},
          "subject_box":{"type":"ARRAY","items":{"type":"INTEGER"},"description":"Where the subject is now: [ymin, xmin, ymax, xmax], 0 to 1000"},
          "moves":{"type":"ARRAY","description":"2 or 3 moves, most important first; empty if ready","items":{
            "type":"OBJECT","properties":{
              "action":{"type":"STRING","description":"The move as a short instruction, max 12 words"},
              "why":{"type":"STRING","description":"Why it makes the photo better, max 15 words"},
              "check":{"type":"STRING","enum":["angle","zoom","frame","other"]},
              "zoom":{"type":"NUMBER","description":"Only when check is zoom: the zoom button to tap, e.g. 2"},
              "angle":{"type":"STRING","enum":["above","diner","eye"],"description":"Only when check is angle"},
              "target_x":{"type":"NUMBER","description":"Only when check is frame: where the subject's centre should be, 0 left to 1 right"},
              "target_y":{"type":"NUMBER","description":"Only when check is frame: 0 top to 1 bottom"},
              "size":{"type":"NUMBER","description":"Only when check is frame: share of the picture's height the subject should fill"}},
            "required":["action","why","check"]}}},
         "required":["seen","subject","ready","kind","frame","moves"]}
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
    fun check(frame: Bitmap, advice: Advice, context: String, alreadyDone: List<Boolean> = emptyList()): Reply<Review> {
        val list = advice.moves.mapIndexed { i, m -> "${i + 1}. ${m.action}${if (alreadyDone.getOrElse(i) { false }) " (already done)" else ""}" }.joinToString("\n")
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
                // temperature 0: the same scene gets the same steps, instead of a slightly different answer each time
                // (group feedback round 3: instructions still changing).
                JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema).put("temperature", 0).apply {
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
        val kind = when (o.optString("kind")) { "scene" -> ShotKind.Scene; "living" -> ShotKind.Living; else -> ShotKind.TableTop }
        val asked = when (o.optString("frame")) {
            "thirds" -> Guide.Thirds; "centre" -> Guide.Centre; "front_back" -> Guide.FrontBack; "diagonal" -> Guide.Diagonal
            "triangle" -> Guide.Triangle; "grid" -> Guide.Grid; "circle" -> Guide.Circle; "spiral" -> Guide.Spiral; else -> null
        }
        // Arrangement frames are for things on a table. A street or a bridge got a diagonal line to "align" with, which
        // nobody could follow (group feedback 2026-10-07), so scenes and living subjects only get thirds or centre.
        val frame = if (kind != ShotKind.TableTop && asked != null && asked != Guide.Thirds && asked != Guide.Centre) Guide.Thirds else asked
        // Targets sit exactly on the drawn frame: snap each to the frame's nearest spot when it's close. On the thirds
        // grid any cross will do, so use the one nearest the subject (a classmate saw a tree being sent to the top corner,
        // 2026-10-06); a subject taller than half the picture only moves sideways onto a third line.
        val spots = frame?.let { targets(it, frameSlots(it)) }.orEmpty()
        fun snap(x: Float?, y: Float?): Pair<Float?, Float?> {
            if (x == null || y == null || spots.isEmpty()) return x to y
            val near = spots.minBy { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
            return if (hypot(near.x - x, near.y - y) < 0.2f) near.x to near.y else x to y
        }
        fun thirdsFor(x: Float?, y: Float?): Pair<Float?, Float?> {
            if (frame != Guide.Thirds || box == null || x == null || y == null) return x to y
            val nx = if (box.centerX() < 0.5f) 1f / 3 else 2f / 3
            if (box.height() > 0.5f) return nx to box.centerY().coerceIn(0.3f, 0.7f)
            return nx to (if (box.centerY() < 0.5f) 1f / 3 else 2f / 3)
        }
        var frameMoves = 0
        Advice(
            seen = o.optString("seen"),
            subject = o.optString("subject"),
            ready = o.optBoolean("ready"),
            moves = (0 until moves.length()).map { i ->
                val m = moves.getJSONObject(i)
                val check = when (m.optString("check")) { "angle" -> CheckBy.Angle; "frame" -> CheckBy.Frame; "zoom" -> CheckBy.Zoom; else -> CheckBy.Other }
                val zoomTo = if (m.has("zoom")) m.optDouble("zoom").toFloat().takeIf { it in 0.4f..10f } else null
                val angle = when (m.optString("angle")) { "above" -> ShotAngle.Above; "diner" -> ShotAngle.Diner; "eye" -> ShotAngle.Eye; else -> null }
                fun frac(key: String) = if (m.has(key)) m.optDouble(key).toFloat().takeIf { it in 0f..1f } else null
                val (sx, sy) = snap(frac("target_x"), frac("target_y"))
                val (tx, ty) = thirdsFor(sx, sy)
                Move(
                    action = m.optString("action"),
                    why = m.optString("why"),
                    // A check the phone can't actually do falls back to "ask again".
                    check = when {
                        check == CheckBy.Angle && angle == null -> CheckBy.Other
                        check == CheckBy.Zoom && zoomTo == null -> CheckBy.Other
                        check == CheckBy.Frame && (tx == null || ty == null) -> CheckBy.Other
                        // Only one position move: a second one would pull the subject to another spot.
                        check == CheckBy.Frame && frameMoves++ > 0 -> CheckBy.Other
                        else -> check
                    },
                    // Never ask for more than 60% of the height: closer than that pushed people into distortion,
                    // their own shadow and out-of-focus shots (group feedback 2026-10-07).
                    angle = angle, targetX = tx, targetY = ty, size = frac("size")?.coerceAtMost(0.6f), zoom = zoomTo,
                )
            }.filter { it.action.isNotBlank() }.take(3),
            subjectBox = box,
            frame = frame,
            frameWhy = o.optString("frame_why"),
            kind = kind,
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
