package com.composepro.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composepro.app.camera.Thing
import com.composepro.app.camera.TipDecision
import com.composepro.app.guide.Alignment as GuideAlignment
import com.composepro.app.guide.Guide
import com.composepro.app.guide.Spiral
import com.composepro.app.guide.targets
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/** Quiet white guide lines and target dots. Dots turn green as things land on them; all lines go green when done. */
@Composable
fun GuideLayer(guide: Guide, count: Int, alignment: GuideAlignment?, visible: Boolean, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(250), label = "guideAlpha")
    Canvas(modifier.alpha(a)) {
        val w = size.width; val h = size.height
        val done = alignment?.done == true
        val line = if (done) CP.Right.copy(alpha = 0.9f) else CP.OnDark.copy(alpha = 0.55f)
        val stroke = 1.5f * density
        val dashed = PathEffect.dashPathEffect(floatArrayOf(6f * density, 6f * density))
        val t = targets(guide, count).map { Offset(it.x * w, it.y * h) }
        when (guide) {
            Guide.Thirds -> {
                for (i in 1..2) {
                    drawLine(line, Offset(w * i / 3, 0f), Offset(w * i / 3, h), stroke)
                    drawLine(line, Offset(0f, h * i / 3), Offset(w, h * i / 3), stroke)
                }
            }
            Guide.Centre -> {
                val c = t[0]; val s = 36f * density
                drawLine(line, c - Offset(0f, s), c + Offset(0f, s), stroke)
                drawLine(line, c - Offset(s, 0f), c + Offset(s, 0f), stroke)
            }
            Guide.Triangle -> {
                val p = Path().apply { moveTo(t[0].x, t[0].y); lineTo(t[1].x, t[1].y); lineTo(t[2].x, t[2].y); close() }
                drawPath(p, line, style = Stroke(stroke, pathEffect = dashed))
            }
            Guide.Diagonal -> drawLine(line, Offset(0.1f * w, 0.18f * h), Offset(0.9f * w, 0.82f * h), stroke, pathEffect = dashed)
            Guide.Spiral -> {
                val p = Path()
                var tt = 0f
                val max = Spiral.maxT(w, h)
                while (tt <= max) {
                    val pt = Spiral.point(tt, w, h)
                    if (tt == 0f) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                    tt += 0.08f
                }
                drawPath(p, line, style = Stroke(stroke))
            }
        }
        t.forEachIndexed { i, c ->
            val hit = alignment?.hits?.getOrNull(i) == true
            if (hit) drawCircle(CP.Right, 7f * density, c)
            else drawCircle(CP.OnDark.copy(alpha = 0.8f), 7f * density, c, style = Stroke(2f * density))
        }
    }
}

/** The edge around the main thing: red with a tip, green with ✓ when everything's right. */
@Composable
fun EdgeLayer(main: Thing?, edge: EdgeState, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (main == null || edge == EdgeState.None) return@Canvas
        val b = main.box
        val padX = b.width() * size.width * 0.06f
        val padY = b.height() * size.height * 0.06f
        val left = b.left * size.width - padX
        val top = b.top * size.height - padY
        val ew = b.width() * size.width + padX * 2
        val eh = b.height() * size.height + padY * 2
        val color = if (edge == EdgeState.Right) CP.Right else CP.Off
        drawRoundRect(color, Offset(left, top), Size(ew, eh), CornerRadius(28f * density), style = Stroke(3f * density))
        if (edge == EdgeState.Right) {
            val c = Offset(left + ew, top)
            drawCircle(CP.Right, 14f * density, c)
            val s = 5f * density
            drawLine(CP.OnDark, c + Offset(-s, 0f), c + Offset(-s * 0.2f, s * 0.8f), 2.5f * density)
            drawLine(CP.OnDark, c + Offset(-s * 0.2f, s * 0.8f), c + Offset(s * 1.1f, -s * 0.8f), 2.5f * density)
        }
    }
}

/** The tip capsule: line 1 is the action, line 2 the reason; "Not right?" sits underneath as its own button. */
@Composable
fun TipCapsule(tip: TipDecision, onNotRight: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = tip.isTip,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 },
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.widthIn(max = 320.dp).clip(CPShape.Pill).background(CP.Glass).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tip.line1, style = CPType.BodyStrong, color = CP.OnDark, textAlign = TextAlign.Center)
                Text(tip.line2, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f), textAlign = TextAlign.Center)
            }
            Box(
                Modifier.padding(top = CPSpace.S1).clip(CPShape.Pill).background(CP.Glass)
                    .clickable(role = Role.Button, onClick = onNotRight)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("Not right?", style = CPType.CaptionMedium, color = CP.OnDark) }
        }
    }
}

/** A short glass note at the top (too dark, tips slow). Not a tip: no edge, no "Not right?". */
@Composable
fun NoteChip(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = text != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(150)), modifier = modifier) {
        Box(Modifier.padding(horizontal = CPSpace.S3).clip(CPShape.Pill).background(CP.Glass).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text ?: "", style = CPType.CaptionMedium, color = CP.OnDark, textAlign = TextAlign.Center)
        }
    }
}

/** Name of the guide, shown briefly when it changes. */
@Composable
fun GuideChip(guide: Guide, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 }, exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 }, modifier = modifier) {
        Column(Modifier.clip(CPShape.Pill).background(CP.Glass).padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(guide.label, style = CPType.CaptionMedium, color = CP.OnDark)
            Text("Swipe to change the guide", style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f))
        }
    }
}

@Composable
fun ZoomChip(zoom: Float, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150)), modifier = modifier) {
        Box(Modifier.clip(CPShape.Pill).background(CP.Glass).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(String.format("%.1f×", zoom), style = CPType.CaptionMedium, color = CP.OnDark)
        }
    }
}
