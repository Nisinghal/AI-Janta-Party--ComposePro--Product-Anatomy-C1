package com.composepro.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
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
import com.composepro.app.guide.GuidePick
import com.composepro.app.guide.P
import com.composepro.app.guide.Ring
import com.composepro.app.guide.Spiral
import com.composepro.app.guide.targets
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/** A thin rule-of-thirds grid, always on while tips are on, so the guide has something to sit in. */
@Composable
fun GridLayer(visible: Boolean, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(250), label = "gridAlpha")
    Canvas(modifier.alpha(a)) {
        val c = CP.OnDark.copy(alpha = 0.35f)
        val sw = 1f * density
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), sw)
            drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), sw)
        }
    }
}

/** Soft white corner marks on everything the camera recognises, so it's clear what it's looking at. */
@Composable
fun ThingsLayer(things: List<Thing>, visible: Boolean, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(250), label = "thingsAlpha")
    Canvas(modifier.alpha(a)) {
        val c = CP.OnDark.copy(alpha = 0.85f)
        val sw = 2.5f * density
        for (th in things) {
            val l = th.box.left * size.width; val t = th.box.top * size.height
            val r = th.box.right * size.width; val b = th.box.bottom * size.height
            val len = minOf(18f * density, (r - l) / 3, (b - t) / 3)
            drawLine(c, Offset(l, t), Offset(l + len, t), sw); drawLine(c, Offset(l, t), Offset(l, t + len), sw)
            drawLine(c, Offset(r, t), Offset(r - len, t), sw); drawLine(c, Offset(r, t), Offset(r, t + len), sw)
            drawLine(c, Offset(l, b), Offset(l + len, b), sw); drawLine(c, Offset(l, b), Offset(l, b - len), sw)
            drawLine(c, Offset(r, b), Offset(r - len, b), sw); drawLine(c, Offset(r, b), Offset(r, b - len), sw)
        }
    }
}

/**
 * The guide lines, plus a circle for each spot a thing should go. Each thing that isn't there yet gets
 * a dot and a dashed arrow to its circle; the circle fills green with ✓ when it lands. All lines go green when done.
 */
@Composable
fun GuideLayer(guide: Guide, count: Int, alignment: GuideAlignment?, things: List<P>, visible: Boolean, modifier: Modifier = Modifier) {
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
            Guide.FrontBack -> drawLine(line, t[0], t[1], stroke, pathEffect = dashed)
            Guide.Grid -> {
                // A dashed frame around the spots, so it reads as rows rather than loose dots.
                val pad = 30f * density
                val l = t.minOf { it.x } - pad; val r = t.maxOf { it.x } + pad
                val tp = t.minOf { it.y } - pad; val b = t.maxOf { it.y } + pad
                drawRoundRect(line, Offset(l, tp), Size(r - l, b - tp), CornerRadius(12f * density), style = Stroke(stroke, pathEffect = dashed))
            }
            Guide.Circle -> drawOval(
                line,
                Offset((Ring.centre.x - Ring.RX) * w, (Ring.centre.y - Ring.RY) * h),
                Size(2 * Ring.RX * w, 2 * Ring.RY * h),
                style = Stroke(stroke, pathEffect = dashed),
            )
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
        val ring = 16f * density
        val matched = alignment?.matches.orEmpty()
        t.forEachIndexed { j, c ->
            val m = matched.firstOrNull { it.target == j }
            when {
                m?.hit == true -> {
                    drawCircle(CP.Right, ring, c)
                    val s = 6f * density
                    drawLine(CP.OnDark, c + Offset(-s, 0f), c + Offset(-s * 0.2f, s * 0.8f), 2.5f * density)
                    drawLine(CP.OnDark, c + Offset(-s * 0.2f, s * 0.8f), c + Offset(s * 1.1f, -s * 0.8f), 2.5f * density)
                }
                m != null -> {
                    drawCircle(CP.Glass, ring, c)
                    drawCircle(CP.OnDark, ring, c, style = Stroke(2.5f * density))
                }
                // A spot no thing is paired with (e.g. 2 things on a triangle): faint, so it doesn't ask for anything.
                else -> drawCircle(CP.OnDark.copy(alpha = 0.4f), 7f * density, c, style = Stroke(1.5f * density))
            }
        }
        // Arrow from each thing to its circle.
        for (m in matched) {
            if (m.hit) continue
            val p = things.getOrNull(m.thing) ?: continue
            val from = Offset(p.x * w, p.y * h)
            val to = t.getOrNull(m.target) ?: continue
            val d = to - from
            val len = d.getDistance()
            if (len < ring * 1.5f) continue
            val u = d / len
            val end = to - u * (ring + 4f * density)
            drawCircle(CP.OnDark, 6f * density, from)
            drawCircle(CP.Accent, 6f * density, from, style = Stroke(1.5f * density))
            drawLine(CP.OnDark, from + u * (8f * density), end, 3f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density, 7f * density)))
            val head = 11f * density
            val side = Offset(-u.y, u.x)
            val arrow = Path().apply {
                moveTo(end.x, end.y)
                lineTo((end - u * head + side * head * 0.6f).x, (end - u * head + side * head * 0.6f).y)
                lineTo((end - u * head - side * head * 0.6f).x, (end - u * head - side * head * 0.6f).y)
                close()
            }
            drawPath(arrow, CP.OnDark)
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

/**
 * The tip card: up to two rows, "Phone" (how to hold or move the phone, with its reason) and
 * "Arrange" (where to move a thing). "Not right?" sits underneath as its own button.
 */
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
                Modifier.widthIn(max = 340.dp).clip(CPShape.Card).background(CP.Glass).padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                if (tip.phone.isNotEmpty()) TipRow("Phone", tip.phone, tip.why.ifEmpty { null })
                if (tip.phone.isNotEmpty() && tip.arrange != null) {
                    Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(CP.OnDark.copy(alpha = 0.2f)))
                }
                tip.arrange?.let { TipRow("Arrange", it, null) }
            }
            Box(
                Modifier.padding(top = CPSpace.S1).clip(CPShape.Pill).background(CP.Glass)
                    .clickable(role = Role.Button, onClick = onNotRight)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("Not right?", style = CPType.CaptionMedium, color = CP.OnDark) }
        }
    }
}

@Composable
private fun TipRow(label: String, text: String, why: String?) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label.uppercase(), style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.6f),
            modifier = Modifier.width(64.dp).padding(top = 2.dp),
        )
        Column {
            Text(text, style = CPType.BodyStrong, color = CP.OnDark)
            why?.let { Text(it, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f)) }
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

/** Which guide the camera picked and for what. Stays while the guide shows; tap to make the camera look again. */
@Composable
fun GuideChip(pick: GuidePick?, visible: Boolean, onLookAgain: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible && pick != null, enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 }, exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 }, modifier = modifier) {
        Column(
            Modifier.clip(CPShape.Pill).background(CP.Glass).clickable(role = Role.Button, onClick = onLookAgain)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${pick?.guide?.label} · ${pick?.seen}", style = CPType.CaptionMedium, color = CP.OnDark)
            Text("Tap to look again", style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f))
        }
    }
}

/** A two-line glass card for what the camera is doing: looking (with a 3-second bar), then what it picked and why. */
@Composable
fun InfoCapsule(visible: Boolean, line1: String, line2: String, modifier: Modifier = Modifier, progress: Float? = null) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(150)), modifier = modifier) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(CPShape.Card).background(CP.Glass).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(line1, style = CPType.BodyStrong, color = CP.OnDark, textAlign = TextAlign.Center)
            Text(line2, style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f), textAlign = TextAlign.Center)
            if (progress != null) {
                val p by animateFloatAsState(progress, tween(200, easing = LinearEasing), label = "scan")
                Box(Modifier.padding(top = 8.dp).width(160.dp).height(3.dp).clip(CPShape.Pill).background(CP.OnDark.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(p).height(3.dp).background(CP.OnDark))
                }
            }
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

/** A ring where the person tapped to focus, shrinking onto the spot like any camera app. */
@Composable
fun FocusRing(at: Offset?, visible: Boolean) {
    val a by animateFloatAsState(if (visible && at != null) 1f else 0f, tween(if (visible) 120 else 300), label = "focusAlpha")
    val grow by animateFloatAsState(if (visible) 1f else 1.25f, tween(250), label = "focusSize")
    Canvas(Modifier.fillMaxSize().alpha(a)) {
        val c = at ?: return@Canvas
        drawCircle(Color.White, 34f * density * (2.2f - grow), c, style = Stroke(2f * density))
        drawCircle(Color.White, 3f * density, c)
    }
}

/** Quick zoom like a phone's own camera: 1× and 2× (only if the camera can zoom that far). The current one is filled. */
@Composable
fun ZoomButtons(zoom: Float, maxZoom: Float, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    val stops = listOf(1f, 2f).filter { it <= maxZoom + 0.01f }
    if (stops.size < 2) return
    Row(modifier.clip(CPShape.Pill).background(CP.Glass).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        stops.forEach { z ->
            val on = kotlin.math.abs(zoom - z) < 0.15f
            Box(
                Modifier.size(40.dp).clip(CPShape.Pill).background(if (on) CP.OnDark else Color.Transparent)
                    .clickable(role = Role.Button) { onZoom(z) }
                    .semantics { contentDescription = "Zoom ${z.toInt()} times" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (on || zoom < 1.05f || z != 1f) "${z.toInt()}×" else String.format("%.1f×", zoom),
                    style = CPType.CaptionMedium, color = if (on) CP.Ink else CP.OnDark,
                )
            }
        }
    }
}
