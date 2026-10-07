package com.composepro.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType
import kotlin.math.PI
import kotlin.math.sin

private val SplashBg = Color(0xFF171717)
private val IconGreen = Color(0xFF2FA84F)

/**
 * Shown on every launch, 3 seconds (user request, 2026-10-06: "3 sec of illustration and motion splash screen").
 * The app icon ("Camera C", chosen 2026-10-07) builds itself: the camera pops in, its top slides up, the lens ring
 * sweeps round into a C, the green lens fills in, the tick draws, then the name fades up.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(3000, easing = LinearEasing))
        onDone()
    }
    val p = t.value
    // Phases across the 3 s (as fractions of the whole)
    fun phase(from: Float, to: Float): Float {
        val x = ((p - from) / (to - from)).coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)   // ease in-out
    }
    val name = phase(0.70f, 0.85f)

    Box(Modifier.fillMaxSize().background(SplashBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CPSpace.S3)) {
            Canvas(Modifier.size(200.dp)) {
                mark(
                    body = phase(0.00f, 0.20f), top = phase(0.12f, 0.26f), ring = phase(0.24f, 0.52f),
                    lens = phase(0.42f, 0.58f), tick = phase(0.56f, 0.70f), flash = phase(0.66f, 0.74f),
                )
            }
            Text(
                "Compose Pro", style = CPType.Display, color = CP.OnDark,
                modifier = Modifier.alpha(name).offset(y = (12 * (1 - name)).dp),
            )
            Text(
                "Steps to a better photo", style = CPType.Body, color = CP.OnDark.copy(alpha = 0.7f),
                modifier = Modifier.alpha(phase(0.78f, 0.92f)),
            )
        }
    }
}

/** A small overshoot, so things pop rather than just grow. */
private fun pop(x: Float) = x + 0.12f * sin(PI.toFloat() * x)

/** The icon's drawing (same geometry as ic_launcher_foreground, in its 1024-unit box), animated by the phase values. */
private fun DrawScope.mark(body: Float, top: Float, ring: Float, lens: Float, tick: Float, flash: Float) {
    val u = size.minDimension / 760f
    val c = Offset(size.width / 2, size.height / 2)
    fun o(x: Float, y: Float) = Offset(c.x + (x - 512f) * u, c.y + (y - 505f) * u)

    scale(0.7f + 0.3f * pop(body), c) {
        // Camera top: rises out of the body
        if (top > 0f) drawRoundRect(
            Color.White.copy(alpha = top), o(380f, 230f + 60f * (1 - top)), Size(264f * u, 120f * u), CornerRadius(36f * u),
        )
        drawRoundRect(Color.White.copy(alpha = body), o(170f, 300f), Size(684f * u, 480f * u), CornerRadius(96f * u))

        // The C: the lens ring sweeps round, leaving the gap on the right
        if (ring > 0f) drawArc(
            SplashBg, 40f, 280f * ring, false, o(512f - 158f, 540f - 158f), Size(316f * u, 316f * u),
            style = Stroke(64f * u, cap = StrokeCap.Round),
        )

        // Green lens, then its tick
        if (lens > 0f) drawCircle(IconGreen, 94f * u * pop(lens), o(530f, 540f))
        if (tick > 0f) {
            val a = o(486f, 540f); val b = o(516.8f, 568.6f); val e = o(574f, 511.4f)
            val tw = 24f * u
            drawLine(Color.White, a, a + (b - a) * (tick * 2f).coerceAtMost(1f), tw, StrokeCap.Round)
            if (tick > 0.5f) drawLine(Color.White, b, b + (e - b) * ((tick - 0.5f) * 2f), tw, StrokeCap.Round)
        }

        // Flash
        if (flash > 0f) drawCircle(IconGreen, 26f * u * pop(flash), o(752f, 380f))
    }
}
