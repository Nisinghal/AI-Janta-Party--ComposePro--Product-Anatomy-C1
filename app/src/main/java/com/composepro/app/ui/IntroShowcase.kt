package com.composepro.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Onboarding showcase (user request 2026-10-07, after Reve's intro on 60fps.design: cards slide into focus by themselves,
 * a floating shutter taps, the photo comes back from a blur, then the next card slides in).
 * Each card plays one of the app's real camera motions: move until the dot is in the ring, tilt until level, zoom 2×.
 * The step's red number turns into a green ✓, the shutter taps, and the clean photo is revealed.
 */
private enum class Demo { Move, Tilt, Zoom }

private data class Slide(val demo: Demo, val step: String, val title: String, val body: String)

private val slides = listOf(
    Slide(Demo.Move, "Move until the dot is in the ring", "Point at anything", "Compose Pro sees what's in the frame and shows you where it should go."),
    Slide(Demo.Tilt, "Tilt until the line is level", "Follow the steps", "Each step has a red number. It turns into a green ✓ when you've done it."),
    Slide(Demo.Zoom, "Tap 2× to get closer", "Then take the shot", "When the steps are green, tap the shutter. It works any time."),
)

private const val CYCLE = 4600   // ms each card plays
private const val PAGES = 3000   // many virtual pages, so the carousel always slides forward
private const val TILT = 14f     // degrees the plant card starts tilted

private fun phase(p: Float, a: Float, b: Float): Float {
    val x = ((p - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}

private fun bump(p: Float, a: Float, peak: Float, b: Float) = when {
    p <= a || p >= b -> 0f
    p < peak -> (p - a) / (peak - a)
    else -> 1 - (p - peak) / (b - peak)
}

@Composable
fun IntroShowcase(modifier: Modifier = Modifier) {
    val first = PAGES / 2 - (PAGES / 2) % slides.size
    val pager = rememberPagerState(initialPage = first) { PAGES }
    val prog = remember { Animatable(0f) }
    var active by remember { mutableIntStateOf(first) }
    LaunchedEffect(pager.settledPage) {
        prog.snapTo(0f)
        active = pager.settledPage
        prog.animateTo(1f, tween(CYCLE, easing = LinearEasing))
        pager.animateScrollToPage(pager.settledPage + 1, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    fun progressOf(page: Int) = when {
        page == active -> prog.value
        page < active -> 1f
        else -> 0f
    }
    val p = prog.value

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // As tall as a 3:4 card (+ the shutter's overhang) and centred, so tall phones don't get a gap above the cards.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val fits = minOf((maxWidth - 88.dp) * 4f / 3f + 38.dp, maxHeight)
            Box(Modifier.fillMaxWidth().height(fits)) {
                HorizontalPager(
                    pager, Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 44.dp), pageSpacing = 14.dp, beyondViewportPageCount = 1,
                ) { page ->
                    Box(Modifier.fillMaxSize().padding(bottom = 38.dp), contentAlignment = Alignment.BottomCenter) {
                        DemoCard(
                            slides[page % slides.size], progressOf(page),
                            Modifier.aspectRatio(3f / 4f, matchHeightConstraintsFirst = true).graphicsLayer {
                                val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                                val s = 1f - 0.08f * off
                                scaleX = s; scaleY = s; alpha = 1f - 0.5f * off
                            },
                        )
                    }
                }
                // The floating shutter, tapped by the demo once the step is green
                DemoShutter(
                    press = bump(p, 0.57f, 0.62f, 0.68f),
                    ripple = if (p in 0.62f..0.82f) phase(p, 0.62f, 0.82f) else 0f,
                    ready = phase(p, 0.50f, 0.56f) * (1 - phase(p, 0.62f, 0.66f)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        val shown = slides[pager.currentPage % slides.size]
        AnimatedContent(
            targetState = shown,
            transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 }) togetherWith fadeOut(tween(150)) },
            label = "caption",
            modifier = Modifier.fillMaxWidth().padding(horizontal = CPSpace.S3).padding(top = CPSpace.S3).heightIn(min = 92.dp),
        ) { s ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.title, style = CPType.Display, color = CP.OnDark, textAlign = TextAlign.Center)
                Text(s.body, style = CPType.Body, color = CP.OnDark.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }

        Row(Modifier.padding(top = CPSpace.S2), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(slides.size) { i ->
                val on = pager.currentPage % slides.size == i
                Box(Modifier.height(6.dp).width(if (on) 22.dp else 6.dp).clip(CPShape.Pill).background(CP.OnDark.copy(alpha = if (on) 1f else 0.3f)))
            }
        }
    }
}

@Composable
private fun DemoShutter(press: Float, ripple: Float, ready: Float, modifier: Modifier = Modifier) {
    Box(modifier.size(76.dp), contentAlignment = Alignment.Center) {
        if (ripple > 0f) Box(
            Modifier.size(72.dp).graphicsLayer { val s = 1f + 0.7f * ripple; scaleX = s; scaleY = s; alpha = 1 - ripple }
                .border(3.dp, CP.OnDark, CircleShape),
        )
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(CP.Accent)
                .border(4.dp, lerp(CP.OnDark, CP.Right, ready), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(56.dp).graphicsLayer { val s = 1f - 0.2f * press; scaleX = s; scaleY = s }.clip(CircleShape).background(CP.OnDark))
        }
    }
}

@Composable
private fun DemoCard(slide: Slide, p: Float, modifier: Modifier = Modifier) {
    val overlays = phase(p, 0.04f, 0.14f) * (1 - phase(p, 0.62f, 0.67f))
    val act = phase(p, 0.18f, 0.50f)        // the camera motion
    val done = phase(p, 0.50f, 0.56f)       // red number → green ✓
    val flash = bump(p, 0.61f, 0.635f, 0.72f)
    val shot = p >= 0.63f
    val reveal = phase(p, 0.64f, 0.86f)     // blur → sharp
    val saved = phase(p, 0.80f, 0.88f)
    val blurDp = if (shot) 18f * (1 - reveal) else 0f

    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Color.Black)) {
        Canvas(
            Modifier.fillMaxSize()
                .then(if (blurDp > 0.5f) Modifier.blur(blurDp.dp) else Modifier)
                .graphicsLayer { val z = if (shot) 1.05f - 0.05f * reveal else 1f; scaleX = z; scaleY = z },
        ) {
            when (slide.demo) {
                Demo.Move -> tableScene(act)
                Demo.Tilt -> plantScene(act)
                Demo.Zoom -> hillScene(act)
            }
        }
        Canvas(Modifier.fillMaxSize().alpha(overlays)) {
            grid()
            when (slide.demo) {
                Demo.Move -> moveOverlay(act, done)
                Demo.Tilt -> tiltOverlay(act, done)
                Demo.Zoom -> zoomOverlay(act, done)
            }
        }
        StepChip(slide.step, done, Modifier.align(Alignment.TopCenter).padding(top = 14.dp, start = 10.dp, end = 10.dp).alpha(overlays))
        when (slide.demo) {
            Demo.Tilt -> GlassLabel("${(TILT * (1 - act)).roundToInt()}°", Modifier.align(Alignment.Center).padding(top = 72.dp).alpha(overlays))
            Demo.Zoom -> {
                ZoomPills(act > 0.01f, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 48.dp).alpha(overlays))
                GlassLabel("%.1f×".format(1f + act), Modifier.align(Alignment.Center).alpha(overlays * phase(p, 0.17f, 0.21f) * (1 - phase(p, 0.5f, 0.56f))))
            }
            else -> {}
        }
        GlassLabel("✓  Saved to your photos", Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp).alpha(saved))
        if (flash > 0f) Box(Modifier.fillMaxSize().alpha(flash).background(Color.White))
    }
}

@Composable
private fun StepChip(text: String, done: Float, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CPShape.Pill).background(Color.Black.copy(alpha = 0.55f)).padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(lerp(CP.Off, CP.Right, done)), contentAlignment = Alignment.Center) {
            Text(if (done > 0.5f) "✓" else "1", style = CPType.CaptionMedium, color = CP.OnDark)
        }
        Text(text, style = CPType.CaptionMedium, color = CP.OnDark, maxLines = 1)
    }
}

@Composable
private fun GlassLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CPShape.Pill).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, style = CPType.CaptionMedium, color = CP.OnDark)
    }
}

@Composable
private fun ZoomPills(twoX: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.clip(CPShape.Pill).background(Color.Black.copy(alpha = 0.55f)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((label, on) in listOf("1×" to !twoX, "2×" to twoX)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(if (on) CP.OnDark else Color.Transparent), contentAlignment = Alignment.Center) {
                Text(label, style = CPType.CaptionMedium, color = if (on) CP.Ink else CP.OnDark)
            }
        }
    }
}

// ---------- Overlays: the same marks the camera screen uses ----------

private fun DrawScope.grid() {
    val c = Color.White.copy(alpha = 0.35f)
    val sw = 1.dp.toPx()
    for (i in 1..2) {
        drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), sw)
        drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), sw)
    }
}

private fun DrawScope.ring(at: Offset, r: Float, done: Float) {
    if (done < 1f) drawCircle(Color.White.copy(alpha = 1 - done), r, at, style = Stroke(2.5.dp.toPx()))
    if (done > 0f) {
        drawCircle(CP.Right, r * (0.7f + 0.3f * done), at, alpha = done)
        val s = r * 0.42f
        val a = at + Offset(-s, 0f)
        val b = at + Offset(-0.3f * s, 0.65f * s)
        val c = at + Offset(s, -0.65f * s)
        val tw = 2.5.dp.toPx()
        drawLine(Color.White, a, a + (b - a) * (done * 2).coerceAtMost(1f), tw, StrokeCap.Round)
        if (done > 0.5f) drawLine(Color.White, b, b + (c - b) * ((done - 0.5f) * 2), tw, StrokeCap.Round)
    }
}

private fun DrawScope.moveOverlay(act: Float, done: Float) {
    val (start, target) = movePoints()
    ring(target, size.width * 0.08f, done)
    drawCircle(Color.White.copy(alpha = 1 - done), 6.dp.toPx(), start + (target - start) * act)
}

private fun DrawScope.tiltOverlay(act: Float, done: Float) {
    val mid = Offset(size.width / 2, size.height / 2)
    val ref = Color.White.copy(alpha = 0.7f)
    val sw = 3.dp.toPx()
    drawLine(ref, Offset(size.width * 0.06f, mid.y), Offset(size.width * 0.15f, mid.y), sw, StrokeCap.Round)
    drawLine(ref, Offset(size.width * 0.85f, mid.y), Offset(size.width * 0.94f, mid.y), sw, StrokeCap.Round)
    rotate(-TILT * (1 - act), mid) {
        drawLine(lerp(Color.White, CP.Right, done), Offset(size.width * 0.2f, mid.y), Offset(size.width * 0.8f, mid.y), sw, StrokeCap.Round)
    }
    drawCircle(lerp(Color.White, CP.Right, done), 5.dp.toPx(), mid)
}

private fun DrawScope.zoomOverlay(act: Float, done: Float) {
    // Where a 2× photo will reach, as seen at the current zoom: it grows to the card's edges.
    val f = zoomFocus()
    val k = (1f + act) / 2f
    val r = Rect(f.x - f.x * k, f.y - f.y * k, f.x + (size.width - f.x) * k, f.y + (size.height - f.y) * k)
    val inset = 2.dp.toPx()
    drawRect(
        Color.White.copy(alpha = 1 - done), Offset(r.left + inset, r.top + inset), Size(r.width - 2 * inset, r.height - 2 * inset),
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

// ---------- Scenes (simple flat illustrations, like the app's other pictures) ----------

private fun DrawScope.movePoints() = Offset(size.width * 0.36f, size.height * 0.56f) to Offset(size.width * 2 / 3, size.height * 2 / 3)

private fun DrawScope.zoomFocus() = Offset(size.width * 0.64f, size.height * 0.5f)

/** A cup on a table. The view slides until the cup sits on the lower-right thirds point. */
private fun DrawScope.tableScene(act: Float) {
    val w = size.width; val h = size.height
    val (start, target) = movePoints()
    val d = (target - start) * act
    val edge = 0.44f * h
    translate(d.x, d.y) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF1E8DC), Color(0xFFDCCBB6)), -h, edge), Offset(-w, -h), Size(3 * w, h + edge))
        drawRect(Color(0xFFFFFFFF).copy(alpha = 0.35f), Offset(0.62f * w, 0.02f * h), Size(0.3f * w, 0.3f * h))
        drawRect(Brush.verticalGradient(listOf(Color(0xFFBF8B5E), Color(0xFF8A5835)), edge, 2f * h), Offset(-w, edge), Size(3 * w, 2f * h))
        drawRect(Color(0xFFD29E70), Offset(-w, edge), Size(3 * w, 0.012f * h))
        cup(start, w)
        // a small plate of biscuits at the side, for scale
        drawOval(Color.Black.copy(alpha = 0.12f), Offset(start.x - 0.5f * w, start.y + 0.11f * w), Size(0.3f * w, 0.06f * w))
        drawOval(Color(0xFFF7F4EF), Offset(start.x - 0.5f * w, start.y + 0.09f * w), Size(0.3f * w, 0.07f * w))
        drawOval(Color(0xFFD9A15F), Offset(start.x - 0.44f * w, start.y + 0.085f * w), Size(0.1f * w, 0.045f * w))
        drawOval(Color(0xFFC98E4E), Offset(start.x - 0.36f * w, start.y + 0.095f * w), Size(0.1f * w, 0.045f * w))
    }
}

private fun DrawScope.cup(c: Offset, w: Float) {
    val cw = 0.24f * w; val ch = 0.2f * w
    val top = c.y - ch / 2; val bot = c.y + ch / 2
    val white = Color(0xFFF7F4EF)
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(c.x - cw * 0.8f, bot - ch * 0.02f), Size(cw * 1.6f, ch * 0.3f))
    drawOval(white, Offset(c.x - cw * 0.72f, bot - ch * 0.12f), Size(cw * 1.44f, ch * 0.3f))
    drawOval(Color(0xFFE2DBD1), Offset(c.x - cw * 0.42f, bot - ch * 0.05f), Size(cw * 0.84f, ch * 0.14f))
    drawArc(white, -90f, 180f, false, Offset(c.x + cw * 0.3f, top + ch * 0.18f), Size(cw * 0.36f, ch * 0.46f), style = Stroke(cw * 0.08f))
    val body = Path().apply {
        moveTo(c.x - cw / 2, top); lineTo(c.x + cw / 2, top)
        lineTo(c.x + cw * 0.36f, bot - ch * 0.06f); lineTo(c.x + cw * 0.26f, bot)
        lineTo(c.x - cw * 0.26f, bot); lineTo(c.x - cw * 0.36f, bot - ch * 0.06f); close()
    }
    drawPath(body, Brush.horizontalGradient(listOf(Color.White, Color(0xFFE3DCD2)), c.x - cw / 2, c.x + cw / 2))
    drawOval(Color(0xFFEDE8E1), Offset(c.x - cw / 2, top - ch * 0.1f), Size(cw, ch * 0.2f))
    drawOval(Color(0xFF6B4226), Offset(c.x - cw * 0.44f, top - ch * 0.07f), Size(cw * 0.88f, ch * 0.14f))
}

/** A plant on a shelf, seen with the phone tilted. It straightens as the line levels. */
private fun DrawScope.plantScene(act: Float) {
    val w = size.width; val h = size.height
    val mid = Offset(w / 2, h / 2)
    rotate(-TILT * (1 - act), mid) {
        scale(1.4f, mid) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFE2EAE3), Color(0xFFC8D6CB)), 0f, 0.7f * h), Offset(-w, -h), Size(3 * w, 1.7f * h))
            drawRect(Color(0xFFF8F6F1), Offset(0.62f * w, 0.18f * h), Size(0.22f * w, 0.18f * h))
            drawRect(Color(0xFFE9B98C), Offset(0.645f * w, 0.2f * h), Size(0.17f * w, 0.14f * h))
            drawCircle(Color(0xFFF7E3C6), 0.025f * w, Offset(0.7f * w, 0.25f * h))
            drawRect(Color(0xFFEFE6D8), Offset(-w, 0.7f * h), Size(3 * w, 0.06f * h))
            drawRect(Color(0xFFD3C3AB), Offset(-w, 0.76f * h), Size(3 * w, h))
            val base = Offset(0.42f * w, 0.58f * h)
            for ((angle, len, color) in listOf(
                Triple(-48f, 0.30f, Color(0xFF4E9C66)), Triple(42f, 0.28f, Color(0xFF3F8A57)),
                Triple(-18f, 0.36f, Color(0xFF2F7A4A)), Triple(14f, 0.38f, Color(0xFF55A36E)), Triple(-2f, 0.3f, Color(0xFF3A8452)),
            )) {
                rotate(angle, base) { drawOval(color, Offset(base.x - 0.05f * w, base.y - len * w), Size(0.1f * w, len * w)) }
            }
            val pot = Path().apply {
                moveTo(base.x - 0.13f * w, base.y - 0.01f * h); lineTo(base.x + 0.13f * w, base.y - 0.01f * h)
                lineTo(base.x + 0.095f * w, 0.7f * h); lineTo(base.x - 0.095f * w, 0.7f * h); close()
            }
            drawPath(pot, Color(0xFFC8643B))
            drawRect(Color(0xFFB0532F), Offset(base.x - 0.14f * w, base.y - 0.03f * h), Size(0.28f * w, 0.035f * h))
        }
    }
}

/** Hills at sunset with a lone tree. The view zooms 2× into the tree and the sun. */
private fun DrawScope.hillScene(act: Float) {
    val w = size.width; val h = size.height
    scale(1f + act, zoomFocus()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF08F5F), Color(0xFFF7C394), Color(0xFFFCE6CB)), 0f, 0.6f * h), Offset.Zero, Size(w, 0.62f * h))
        drawCircle(Color(0xFFFFF4DE), 0.07f * w, Offset(0.62f * w, 0.42f * h))
        val far = Path().apply {
            moveTo(0f, 0.56f * h)
            cubicTo(0.2f * w, 0.48f * h, 0.38f * w, 0.5f * h, 0.52f * w, 0.55f * h)
            cubicTo(0.7f * w, 0.6f * h, 0.85f * w, 0.5f * h, w, 0.53f * h)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(far, Color(0xFFA9C69B))
        val near = Path().apply {
            moveTo(0f, 0.7f * h)
            cubicTo(0.25f * w, 0.62f * h, 0.5f * w, 0.55f * h, 0.66f * w, 0.56f * h)
            cubicTo(0.82f * w, 0.57f * h, 0.92f * w, 0.62f * h, w, 0.64f * h)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(near, Color(0xFF6FA06C))
        drawRect(Color(0xFF5B4632), Offset(0.655f * w, 0.5f * h), Size(0.014f * w, 0.06f * h))
        drawCircle(Color(0xFF3F7A4A), 0.045f * w, Offset(0.662f * w, 0.49f * h))
        drawCircle(Color(0xFF4A8A55), 0.03f * w, Offset(0.645f * w, 0.47f * h))
        val front = Path().apply {
            moveTo(0f, 0.84f * h)
            cubicTo(0.3f * w, 0.76f * h, 0.6f * w, 0.8f * h, w, 0.76f * h)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(front, Color(0xFF4E8552))
    }
}
