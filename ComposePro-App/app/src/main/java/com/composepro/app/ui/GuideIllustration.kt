package com.composepro.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.composepro.app.ui.theme.CP

/**
 * The welcome picture: how the app works, playing on a loop. A plant on a table in the camera view with the thirds
 * grid; the white dot on it slides along the arrow into the ring, the ring turns green with a ✓, and the step's red
 * number turns green below. Replaces the food-plate drawing (classmate feedback 2026-10-06: better illustration;
 * the app is for any object now, not just food).
 */
@Composable
fun GuideIllustration(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "guide").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Wait, glide (eased), then hold on the green tick.
        val raw = ((t - 0.15f) / 0.45f).coerceIn(0f, 1f)
        val e = raw * raw * (3 - 2 * raw)
        val done = t > 0.62f

        // Wall and table
        drawRect(Color(0xFFEDE7DE))
        drawRect(Color(0xFFD8C2A0), topLeft = Offset(0f, h * 0.68f), size = Size(w, h * 0.32f))
        drawLine(Color(0xFFC4AC86), Offset(0f, h * 0.68f), Offset(w, h * 0.68f), 2f * density)

        // The plant moves with the phone: from left of the line onto the right third line.
        val startX = 0.36f
        val endX = 2f / 3f
        val cx = (startX + (endX - startX) * e) * w
        plant(cx, h * 0.70f, w, h)
        val subject = Offset(cx, h * 0.50f)
        val spot = Offset(endX * w, h * 0.50f)

        // Thirds grid and viewfinder corners
        val grid = Color.White.copy(alpha = 0.75f)
        for (i in 1..2) {
            drawLine(grid, Offset(w * i / 3, 0f), Offset(w * i / 3, h), 1.5f * density)
            drawLine(grid, Offset(0f, h * i / 3), Offset(w, h * i / 3), 1.5f * density)
        }
        corners(w, h)

        // Ring, dot and arrow (the same marks as on the camera screen)
        val ring = 20f * density
        if (done) {
            drawCircle(CP.Right, ring, spot)
            tick(spot, 7f * density)
        } else {
            drawCircle(Color.Black.copy(alpha = 0.35f), ring, spot)
            drawCircle(Color.White, ring, spot, style = Stroke(3f * density))
            val d = spot - subject
            val len = d.getDistance()
            if (len > ring * 1.5f) {
                val u = d / len
                val end = spot - u * (ring + 4f * density)
                drawLine(Color.White, subject + u * (9f * density), end, 3f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density, 7f * density)))
                val head = 11f * density
                val side = Offset(-u.y, u.x)
                val l = end - u * head + side * head * 0.6f
                val r = end - u * head - side * head * 0.6f
                drawPath(Path().apply { moveTo(end.x, end.y); lineTo(l.x, l.y); lineTo(r.x, r.y); close() }, Color.White)
            }
            drawCircle(Color.White, 7f * density, subject)
        }

        // A mini step card: red number → green ✓, second step still to do.
        val cardTop = h * 0.80f
        drawRoundRect(Color(0xFF1C1C1E), Offset(w * 0.06f, cardTop), Size(w * 0.88f, h * 0.16f), CornerRadius(14f * density))
        val r1 = Offset(w * 0.06f + 22f * density, cardTop + h * 0.05f)
        val r2 = Offset(r1.x, cardTop + h * 0.11f)
        drawCircle(if (done) CP.Right else CP.Off, 8f * density, r1)
        if (done) tick(r1, 3.5f * density)
        drawRoundRect(Color.White.copy(alpha = if (done) 0.5f else 0.9f), Offset(r1.x + 16f * density, r1.y - 3f * density), Size(w * 0.5f, 6f * density), CornerRadius(3f * density))
        drawCircle(CP.Off, 8f * density, r2)
        drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(r2.x + 16f * density, r2.y - 3f * density), Size(w * 0.38f, 6f * density), CornerRadius(3f * density))
    }
}

private fun DrawScope.plant(cx: Float, baseY: Float, w: Float, h: Float) {
    val potW = w * 0.16f
    val potH = h * 0.13f
    // Leaves: a few soft ovals fanning out above the pot
    val leaf = Color(0xFF4F8A5B)
    val leafLight = Color(0xFF6FA978)
    val top = baseY - potH
    listOf(
        Triple(-0.09f, -0.16f, leaf), Triple(0.09f, -0.16f, leaf), Triple(0f, -0.22f, leafLight),
        Triple(-0.05f, -0.10f, leafLight), Triple(0.05f, -0.10f, leaf),
    ).forEach { (dx, dy, c) ->
        drawOval(c, Offset(cx + dx * w - w * 0.06f, top + dy * h - h * 0.05f), Size(w * 0.12f, h * 0.11f))
    }
    // Terracotta pot, slightly narrower at the bottom
    val pot = Path().apply {
        moveTo(cx - potW / 2, top); lineTo(cx + potW / 2, top)
        lineTo(cx + potW * 0.38f, baseY); lineTo(cx - potW * 0.38f, baseY); close()
    }
    drawPath(pot, Color(0xFFC0673F))
    drawRect(Color(0xFFA8552F), Offset(cx - potW / 2 - 2f, top), Size(potW + 4f, potH * 0.2f))
    // Its shadow on the table
    drawOval(Color.Black.copy(alpha = 0.08f), Offset(cx - potW * 0.55f, baseY - 4f), Size(potW * 1.1f, 10f))
}

private fun DrawScope.corners(w: Float, h: Float) {
    val m = 12f * density
    val l = 22f * density
    val sw = 3.5f * density
    val c = Color.White
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(c, Offset(x, y), Offset(x + dx * l, y), sw, cap = StrokeCap.Round)
        drawLine(c, Offset(x, y), Offset(x, y + dy * l), sw, cap = StrokeCap.Round)
    }
    corner(m, m, 1f, 1f); corner(w - m, m, -1f, 1f); corner(m, h * 0.76f, 1f, -1f); corner(w - m, h * 0.76f, -1f, -1f)
}

private fun DrawScope.tick(c: Offset, s: Float) {
    drawLine(Color.White, c + Offset(-s, 0f), c + Offset(-s * 0.2f, s * 0.8f), s * 0.4f, cap = StrokeCap.Round)
    drawLine(Color.White, c + Offset(-s * 0.2f, s * 0.8f), c + Offset(s * 1.1f, -s * 0.8f), s * 0.4f, cap = StrokeCap.Round)
}
