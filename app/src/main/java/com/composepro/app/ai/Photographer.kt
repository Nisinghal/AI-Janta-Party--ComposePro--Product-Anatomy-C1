package com.composepro.app.ai

import android.graphics.Bitmap
import android.util.Base64
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.composepro.app.BuildConfig
import java.io.ByteArrayOutputStream
import java.time.Duration
import kotlin.math.max
import kotlin.math.roundToInt

/** One step the person can take right now, with the photographer's reason. */
@JsonClassDescription("One concrete move the person can make right now with their phone or the things in front of them")
data class Move(
    @field:JsonPropertyDescription("The move, as a short instruction (max 12 words), e.g. 'Lower the phone to the height of the flowers.'")
    val action: String = "",
    @field:JsonPropertyDescription("Why it makes the photo better, in plain words (max 15 words)")
    val why: String = "",
)

/** What the photographer says about one frame. */
@JsonClassDescription("A photographer's quick read of one camera frame")
data class Advice(
    @field:JsonPropertyDescription("What is in the frame, in one plain sentence (max 20 words)")
    val seen: String = "",
    @field:JsonPropertyDescription("The subject the photo should be about, in a few words")
    val subject: String = "",
    @field:JsonPropertyDescription("True only if the frame is already a good photo and needs no moves")
    val ready: Boolean = false,
    @field:JsonPropertyDescription("2 or 3 moves, most important first. Empty if ready is true.")
    val moves: List<Move> = emptyList(),
)

sealed interface AskResult {
    data class Ok(val advice: Advice) : AskResult
    data class Failed(val message: String) : AskResult
}

/**
 * "Ask photographer": sends one camera frame to Claude, only when the person taps the button,
 * and gets back a photographer's read of it (user decision, 2026-10-06: hybrid of live on-phone
 * tips + an on-demand photographer). Nothing is sent otherwise.
 */
object Photographer {
    private const val MODEL = "claude-opus-5-5"

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
        If the frame is already good, say so and set ready to true.
    """.trimIndent()

    private val client: AnthropicClient? by lazy {
        BuildConfig.ANTHROPIC_API_KEY.takeIf { it.isNotBlank() }?.let {
            AnthropicOkHttpClient.builder().apiKey(it).timeout(Duration.ofSeconds(45)).build()
        }
    }

    val hasKey get() = BuildConfig.ANTHROPIC_API_KEY.isNotBlank()

    /**
     * Blocking: call from a background thread. [context] is what the phone already knows
     * (angle, zoom, what the on-phone detector saw) so the advice fits how it's being held.
     */
    fun ask(frame: Bitmap, context: String): AskResult {
        val c = client ?: return AskResult.Failed("The photographer needs a Claude API key. Add it to local.properties and rebuild the app.")
        val image = ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                    .data(toJpegBase64(frame))
                    .build(),
            )
            .build()
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4000L)
            .system(SYSTEM)
            .outputConfig(Advice::class.java)
            // If a safety check ever declines, Anthropic re-runs it on a suitable model instead of failing.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessageOfBlockParams(
                listOf(
                    ContentBlockParam.ofImage(image),
                    ContentBlockParam.ofText(TextBlockParam.builder().text(context).build()),
                ),
            )
            .build()
        return try {
            val response = c.messages().create(params)
            if (response.stopReason().map { it.asString() }.orElse("") == "refusal") {
                return AskResult.Failed("The photographer can't help with this one.")
            }
            val advice = response.content().firstNotNullOfOrNull { block -> block.text().orElse(null)?.text() }
            if (advice == null) AskResult.Failed("The photographer couldn't answer this time. Try again.")
            else AskResult.Ok(advice)
        } catch (e: UnauthorizedException) {
            AskResult.Failed("The Claude API key isn't working. Check it in local.properties.")
        } catch (e: RateLimitException) {
            AskResult.Failed("Too many asks in a row. Wait a few seconds and try again.")
        } catch (e: AnthropicServiceException) {
            AskResult.Failed("The photographer had a problem (${e.statusCode()}). Try again.")
        } catch (e: AnthropicIoException) {
            AskResult.Failed("Couldn't reach the photographer. Check your internet and try again.")
        } catch (e: Exception) {
            AskResult.Failed("Something went wrong asking the photographer. Try again.")
        }
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
