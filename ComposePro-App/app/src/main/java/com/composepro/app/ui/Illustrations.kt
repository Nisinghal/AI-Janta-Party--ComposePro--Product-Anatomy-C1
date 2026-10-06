package com.composepro.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import com.composepro.app.ui.theme.CP

/** Simple drawn plate used on First open and the Quick tour. Illustration only, not a photo. */
private fun DrawScope.plate(center: Offset, r: Float, warm: Boolean) {
    drawCircle(Color(0x33000000), r * 1.04f, center + Offset(0f, r * 0.05f))
    drawCircle(Color(0xFFF7F4EF), r, center)
    drawCircle(Color(0xFFE6E1D8), r * 0.82f, center, style = Stroke(r * 0.03f))
    val food = if (warm) listOf(Color(0xFFB8682E), Color(0xFF7A3A16)) else listOf(Color(0xFFE08A4E), Color(0xFFB4542A))
    drawCircle(Brush.radialGradient(food, center, r * 0.62f), r * 0.6f, center)
    drawCircle(Color(0xFFF6EFE2), r * 0.16f, center + Offset(r * 0.22f, -r * 0.12f))
    drawCircle(Color(0xFFF0A033), r * 0.08f, center + Offset(r * 0.22f, -r * 0.12f))
    drawCircle(Color(0xFF7CB35B), r * 0.05f, center + Offset(-r * 0.25f, r * 0.2f))
    drawCircle(Color(0xFF7CB35B), r * 0.04f, center + Offset(-r * 0.1f, r * 0.3f))
}

private fun DrawScope.table(warm: Boolean) {
    val top = if (warm) Color(0xFF6B4A2E) else Color(0xFF9A6B44)
    val bottom = if (warm) Color(0xFF4A2F1B) else Color(0xFF7A5232)
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    if (warm) drawRect(Color(0x33FFB347))
}

private fun DrawScope.edge(center: Offset, r: Float, color: Color) {
    val half = r * 1.18f
    drawRoundRect(color, Offset(center.x - half, center.y - half), Size(half * 2, half * 2), CornerRadius(r * 0.35f), style = Stroke(3f * density))
}

@Composable
fun PlateIllustration(modifier: Modifier, state: EdgeState, warm: Boolean = false) {
    Canvas(modifier) {
        table(warm)
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) * 0.28f
        plate(c, r, warm)
        when (state) {
            EdgeState.Off -> edge(c, r, CP.Off)
            EdgeState.Right -> {
                edge(c, r, CP.Right)
                val tick = Offset(c.x + r * 1.18f, c.y - r * 1.18f)
                drawCircle(CP.Right, 14f * density, tick)
                val s = 5f * density
                drawLine(CP.OnDark, tick + Offset(-s, 0f), tick + Offset(-s * 0.2f, s * 0.8f), 2.5f * density)
                drawLine(CP.OnDark, tick + Offset(-s * 0.2f, s * 0.8f), tick + Offset(s * 1.1f, -s * 0.8f), 2.5f * density)
            }
            EdgeState.None -> Unit
        }
    }
}

enum class EdgeState { None, Off, Right }
