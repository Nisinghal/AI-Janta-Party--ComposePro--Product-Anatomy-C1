package com.composepro.app.ui

import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.composepro.app.AppState
import com.composepro.app.PendingPhoto
import com.composepro.app.Screen
import com.composepro.app.camera.FrameAnalyzer
import com.composepro.app.camera.FrameResult
import com.composepro.app.camera.Thing
import com.composepro.app.camera.Thresholds
import com.composepro.app.camera.TipDecision
import com.composepro.app.camera.TipKind
import com.composepro.app.camera.decide
import com.composepro.app.camera.thingName
import com.composepro.app.camera.rememberTilt
import com.composepro.app.data.PhotoStore
import com.composepro.app.guide.Guide
import com.composepro.app.guide.GuidePick
import com.composepro.app.guide.describe
import com.composepro.app.guide.pickGuide
import com.composepro.app.guide.P
import com.composepro.app.guide.alignment
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 3:4 portrait for preview, analysis and the photo, so what you see is what's analysed and what's saved. */
private val fourByThree = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .build()

/** A tip appears only after the same thing has been seen for about a second (AX_SPEC). */
private const val SETTLE_MS = 900L

/** How long the camera looks before it picks a guide, how long it explains the pick, and how long a changed count must last before it looks again. */
private const val SCAN_MS = 3000L
private const val EXPLAIN_MS = 3500L
private const val RELOOK_MS = 2500L

@Composable
fun CameraScreen(state: AppState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setResolutionSelector(fourByThree).build()
    }
    var frame by remember { mutableStateOf<FrameResult?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { FrameAnalyzer(context) { r -> frame = r } }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setResolutionSelector(fourByThree)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }
    }
    DisposableEffect(Unit) { onDispose { imageAnalysis.clearAnalyzer(); analyzer.close(); analysisExecutor.shutdown() } }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var lastPhoto by remember { mutableStateOf<Uri?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var taking by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var zoomShownAt by remember { mutableLongStateOf(0L) }
    val dismissedIds = remember { mutableStateMapOf<Int, Boolean>() }
    var dismissedUntil by remember { mutableLongStateOf(0L) }
    val tilt by rememberTilt()
    val flash = remember { Animatable(0f) }
    val cover = remember { Animatable(1f) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().setResolutionSelector(fourByThree).build().also { it.surfaceProvider = previewView.surfaceProvider }
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis)
            unavailable = false
            cover.animateTo(0f, tween(350))
        } catch (e: Exception) {
            unavailable = true
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { lastPhoto = withContext(Dispatchers.IO) { PhotoStore.list(context).firstOrNull() } }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2600); toast = null } }
    LaunchedEffect(Unit) { while (true) { delay(200); now = SystemClock.elapsedRealtime() } }

    // ---- 1. Look for 3 seconds, then pick a guide from what was seen and keep it (user decision, 2026-10-05) ----
    val count = frame?.things?.size ?: 0
    var pick by remember { mutableStateOf<GuidePick?>(null) }
    var scanStart by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var pickedAt by remember { mutableLongStateOf(0L) }
    val samples = remember { mutableListOf<List<Thing>>() }
    var mismatchSince by remember { mutableLongStateOf(0L) }
    fun lookAgain() { pick = null; samples.clear(); scanStart = SystemClock.elapsedRealtime(); mismatchSince = 0L }
    LaunchedEffect(frame) {
        val f = frame ?: return@LaunchedEffect
        val t = SystemClock.elapsedRealtime()
        val p = pick
        if (p == null) {
            if (f.meanY < Thresholds.TOO_DARK) { samples.clear(); scanStart = t; return@LaunchedEffect }
            samples += f.things
            if (t - scanStart < SCAN_MS) return@LaunchedEffect
            // The count seen most often over the 3 seconds, so one missed or doubled frame doesn't decide it.
            val usual = samples.groupingBy { it.size }.eachCount().maxBy { it.value }.key
            if (usual == 0) { samples.clear(); scanStart = t; return@LaunchedEffect }
            pick = pickGuide(samples.last { it.size == usual }, tilt.flat)
            pickedAt = t
        } else if (f.things.size != p.slots) {
            // Something added or taken away (not just a missed frame): look again.
            if (mismatchSince == 0L) mismatchSince = t else if (t - mismatchSince > RELOOK_MS) lookAgain()
        } else mismatchSince = 0L
    }
    val scanning = state.tipsOn && pick == null
    val explaining = pick != null && now - pickedAt < EXPLAIN_MS
    val guide = pick?.guide ?: Guide.Centre
    val slots = pick?.slots ?: 1

    // ---- 2. Tips: raw decision, then settled so they don't flicker ----
    val decided = if (!state.tipsOn) TipDecision.NONE else decide(frame, guide, slots, tilt, zoom) { thing ->
        SystemClock.elapsedRealtime() < dismissedUntil || (thing.id != null && dismissedIds[thing.id] == true)
    }
    // While looking and while explaining the pick, only the "too dark" note can show.
    val raw = if ((scanning || explaining) && decided.kind != TipKind.Note) TipDecision.NONE else decided
    var shown by remember { mutableStateOf(TipDecision.NONE) }
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var pendingSince by remember { mutableLongStateOf(0L) }
    LaunchedEffect(raw) {
        val t = SystemClock.elapsedRealtime()
        if (raw.key == shown.key) { shown = raw; pendingKey = null; return@LaunchedEffect }
        if (shown.kind != TipKind.None) shown = TipDecision.NONE          // problem gone → remove instantly
        if (raw.key != pendingKey) { pendingKey = raw.key; pendingSince = t }
    }
    LaunchedEffect(now, pendingKey) {
        val key = pendingKey ?: return@LaunchedEffect
        if (key == raw.key && now - pendingSince >= SETTLE_MS) { shown = raw; pendingKey = null }
    }
    val things = frame?.things.orEmpty()
    val points = things.map { P(it.cx, it.cy) }
    val guideAlignment = if (points.isEmpty() || pick == null) null else alignment(guide, points, slots)
    val dark = shown.kind == TipKind.Note
    val marksVisible = state.tipsOn && count > 0 && !dark
    val guideVisible = marksVisible && pick != null
    val scanProgress = ((now - scanStart).toFloat() / SCAN_MS).coerceIn(0f, 1f)

    fun takePhoto() {
        if (taking || unavailable) return
        taking = true
        val reminder = if (shown.isTip) shown.remind else null
        val name = frame?.let { f -> thingName(f.things.firstOrNull()?.category) } ?: "Photo"
        scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(180)) }
        imageCapture.takePicture(
            PhotoStore.outputOptions(context),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    taking = false
                    val uri = output.savedUri ?: return
                    state.pending = PendingPhoto(uri, name, reminder)
                    state.screen = Screen.Review
                }

                override fun onError(e: ImageCaptureException) {
                    taking = false
                    toast = if (e.imageCaptureError == ImageCapture.ERROR_FILE_IO)
                        "Your phone's storage is full, so this photo wasn't saved. Free up some space and try again."
                    else "That photo didn't save. Try again."
                }
            },
        )
    }

    fun notRight() {
        val main = shown.main
        if (main?.id != null) dismissedIds[main.id] = true else dismissedUntil = SystemClock.elapsedRealtime() + 15_000
        state.addNotRight(main?.category?.let { thingName(it) } ?: "Something")
        shown = TipDecision.NONE
        toast = "Got it. No more tips for this one."
    }

    // Pinch to zoom.
    val gestures = Modifier.pointerInput(camera) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.pressed }) break
                if (event.changes.size > 1) {
                    val cam = camera ?: continue
                    val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, maxZoom)
                    cam.cameraControl.setZoomRatio(zoom)
                    zoomShownAt = SystemClock.elapsedRealtime()
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(CP.CameraBar).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(CPShape.Sheet).then(gestures)) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            GridLayer(state.tipsOn && !dark, Modifier.fillMaxSize())
            ThingsLayer(things, marksVisible, Modifier.fillMaxSize())
            GuideLayer(guide, slots, guideAlignment, points, guideVisible, Modifier.fillMaxSize())
            EdgeLayer(shown.main, shown.edge, Modifier.fillMaxSize())
            TipCapsule(shown, ::notRight, Modifier.align(Alignment.TopCenter).padding(top = CPSpace.S2, start = CPSpace.S3, end = CPSpace.S3))
            NoteChip(if (dark) shown.line1 else null, Modifier.align(Alignment.TopCenter).padding(top = CPSpace.S2))
            val top = Modifier.align(Alignment.TopCenter).padding(top = CPSpace.S2, start = CPSpace.S3, end = CPSpace.S3)
            InfoCapsule(
                visible = scanning && !dark,
                line1 = if (count == 0 && now - scanStart > 1500) "Point at what you want to shoot." else "Looking at what's here…",
                line2 = if (count == 0) "I'll mark everything I recognise." else "Hold still. Found ${describe(things)} so far.",
                progress = scanProgress,
                modifier = top,
            )
            InfoCapsule(
                visible = explaining && !dark,
                line1 = pick?.let { "I see ${it.seen}. Let's use ${it.guide.label}." } ?: "",
                line2 = pick?.why ?: "",
                modifier = top,
            )
            GuideChip(pick, guideVisible && !explaining, ::lookAgain, Modifier.align(Alignment.BottomCenter).padding(bottom = CPSpace.S2))
            ZoomChip(zoom, now - zoomShownAt < 900, Modifier.align(Alignment.Center))
            Box(Modifier.fillMaxSize().alpha(flash.value).background(CP.OnDark))
            Box(Modifier.fillMaxSize().alpha(cover.value).background(CP.CameraBar))
            if (unavailable) {
                Box(Modifier.fillMaxSize().background(CP.CameraBar).padding(CPSpace.S4), contentAlignment = Alignment.Center) {
                    Text("Camera isn't available right now. Close other apps using it and try again.", style = CPType.Body, color = CP.OnDark, textAlign = TextAlign.Center)
                }
            }
            toast?.let { GlassToast(it, Modifier.align(Alignment.BottomCenter).padding(bottom = CPSpace.S3)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth().padding(horizontal = CPSpace.S4), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(CPSpace.Tap).clip(CPShape.Thumb).border(2.dp, CP.OnDark.copy(alpha = 0.85f), CPShape.Thumb)
                        .clickable(role = Role.Button) { state.screen = Screen.Gallery }
                        .semantics { contentDescription = "Your photos" },
                ) { lastPhoto?.let { PhotoImage(it, 160, Modifier.fillMaxSize()) } }
                Box(
                    Modifier.size(72.dp).alpha(if (unavailable) 0.4f else 1f).clip(CPShape.Pill).border(4.dp, CP.OnDark, CPShape.Pill)
                        .clickable(enabled = !unavailable, role = Role.Button, onClick = ::takePhoto)
                        .semantics { contentDescription = "Take photo" },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(58.dp).clip(CPShape.Pill).background(CP.OnDark)) }
                Box(
                    Modifier.size(CPSpace.Tap).clip(CPShape.Pill).background(CP.Glass)
                        .clickable(role = Role.Button) { state.screen = Screen.Settings },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Settings, "Settings", tint = CP.OnDark, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
fun GlassToast(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = CPSpace.S3).clip(CPShape.Pill).background(CP.Glass).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, style = CPType.CaptionMedium, color = CP.OnDark, textAlign = TextAlign.Center)
    }
}
