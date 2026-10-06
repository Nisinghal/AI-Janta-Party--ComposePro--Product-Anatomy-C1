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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/**
 * Shown on every launch, 3 seconds (user request, 2026-10-06: "3 sec of illustration and motion splash screen").
 * The app icon draws itself and tells the app's story: viewfinder corners snap in, the thirds grid fades in, the
 * white dot (the subject) slides into the ring, the ring turns green with a tick, then the name fades up.
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
    val corners = phase(0.00f, 0.18f)
    val grid = phase(0.12f, 0.30f)
    val dotIn = phase(0.10f, 0.25f)
    val slide = phase(0.30f, 0.62f)
    val green = phase(0.62f, 0.72f)
    val name = phase(0.70f, 0.85f)

    Box(Modifier.fillMaxSize().background(Color(0xFF171717)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CPSpace.S3)) {
            Canvas(Modifier.size(200.dp)) { mark(corners, grid, dotIn, slide, green) }
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

/** The icon's drawing (same geometry as ic_launcher_foreground, 108-unit box), animated by the phase values. */
private fun DrawScope.mark(corners: Float, grid: Float, dotIn: Float, slide: Float, green: Float) {
    val u = size.minDimension / 108f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)

    // Soft disc behind, like the icon background
    drawCircle(Color(0xFF232323), 46f * u * (0.85f + 0.15f * corners), o(54f, 54f), alpha = corners)

    // Thirds grid
    val g = Color.White.copy(alpha = 0.4f * grid)
    for (x in listOf(45f, 63f)) drawLine(g, o(x, 31f), o(x, 31f + 46f * grid), 1.6f * u, StrokeCap.Round)
    for (y in listOf(45f, 63f)) drawLine(g, o(31f, y), o(31f + 46f * grid, y), 1.6f * u, StrokeCap.Round)

    // Viewfinder corners: slide in from further out and grow to full length
    val inset = 8f * (1 - corners)
    val len = 10f * corners
    val sw = 4f * u
    val w = Color.White.copy(alpha = corners)
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        val cx = x - dx * inset; val cy = y - dy * inset
        drawLine(w, o(cx, cy), o(cx + dx * len, cy), sw, StrokeCap.Round)
        drawLine(w, o(cx, cy), o(cx, cy + dy * len), sw, StrokeCap.Round)
    }
    corner(29f, 29f, 1f, 1f); corner(79f, 29f, -1f, 1f); corner(29f, 79f, 1f, -1f); corner(79f, 79f, -1f, -1f)

    // The spot: an empty ring that fills green with a tick when the dot arrives
    val spot = o(63f, 45f)
    if (green < 1f) drawCircle(Color.White.copy(alpha = grid * (1 - green)), 9f * u, spot, style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * u))
    if (green > 0f) {
        drawCircle(CP.Right, 9f * u * (0.6f + 0.4f * green), spot, alpha = green)
        val s = 4.2f * u
        val a = spot + Offset(-s, 0.2f * u)
        val b = spot + Offset(-1.1f * u, 3.3f * u)
        val c = spot + Offset(4.4f * u, -2.8f * u)
        val tw = 2.6f * u
        val ab = (green * 2f).coerceAtMost(1f)
        val bc = ((green - 0.5f) * 2f).coerceIn(0f, 1f)
        drawLine(Color.White, a, a + (b - a) * ab, tw, StrokeCap.Round)
        if (bc > 0f) drawLine(Color.White, b, b + (c - b) * bc, tw, StrokeCap.Round)
    }

    // The subject: a white dot gliding from bottom-left into the ring
    val start = o(44f, 66f)
    val pos = start + (spot - start) * slide
    if (green < 1f) drawCircle(Color.White.copy(alpha = dotIn * (1 - green)), 3.5f * u, pos)
}
