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
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.runtime.SideEffect
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
import com.composepro.app.ai.CheckBy
import com.composepro.app.ai.Coach
import com.composepro.app.ai.Reply
import com.composepro.app.ai.liveCheck
import com.composepro.app.ai.matchSubject
import android.graphics.RectF
import com.composepro.app.ai.Photographer
import com.composepro.app.camera.Tilt
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

/** The photo itself: 3:4 at the camera's full resolution, with the phone's best processing (phone test 2026-10-06: photos looked low quality). */
private val fullPhoto = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
    .build()

/** A tip appears only after the same thing has been seen for about a second (AX_SPEC). */
private const val SETTLE_MS = 900L

/** How long the camera looks before it picks a guide, how long it explains the pick, and how long a changed count must last before it looks again. */
private const val SCAN_MS = 3000L
private const val EXPLAIN_MS = 2000L
private const val RELOOK_MS = 4000L

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
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setResolutionSelector(fullPhoto).build()
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
    // True while the photographer's steps are on screen. Following a step changes what's in view (things come and go
    // as the phone moves), which used to count as a new scene and wipe the steps (phone test 2026-10-06).
    val planUp = remember { mutableStateOf(false) }
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
            pick = pickGuide(samples.last { it.size == usual }, tilt)
            pickedAt = t
        } else if (planUp.value) {
            mismatchSince = 0L
        } else if (f.things.size != p.count || (p.slots == 1 && f.things.none { it.id == p.mainId })) {
            // Something added or taken away, or the phone pointed somewhere else, for a good while
            // (not a missed frame or a hand passing through): look again. Tapping the label also does it.
            if (mismatchSince == 0L) mismatchSince = t else if (t - mismatchSince > RELOOK_MS) lookAgain()
        } else mismatchSince = 0L
    }
    val scanning = state.tipsOn && pick == null
    val explaining = pick != null && now - pickedAt < EXPLAIN_MS
    val guide = pick?.guide ?: Guide.Centre
    val slots = pick?.slots ?: 1
    val currentPick = pick

    // ---- 2. Tips: raw decision, then settled so they don't flicker ----
    val decided = if (!state.tipsOn || currentPick == null) darkOnly(frame) else decide(frame, currentPick, tilt, zoom) { thing ->
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

    // ---- Ask photographer: one frame to Gemini, only when tapped. While its plan is up, live tips hide
    // (one voice at a time) and each step ticks itself when the phone can measure it (user feedback 2026-10-06). ----
    var busy by remember { mutableStateOf(false) }
    var coach by remember { mutableStateOf<Coach?>(null) }
    var askError by remember { mutableStateOf<String?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var subjectBox by remember { mutableStateOf<RectF?>(null) }
    val streak = remember { mutableStateMapOf<Int, Int>() }
    val liveOk = remember { mutableStateMapOf<Int, Boolean>() }

    fun phoneContext(): String {
        val held = when {
            tilt == Tilt.Unknown -> "unknown"
            tilt.flat -> "pointing straight down at a table"
            tilt.offFlatDeg < 60f -> "tilted down, about ${tilt.offFlatDeg.toInt()}° from pointing straight down"
            else -> "upright, at about eye level"
        }
        return "How the phone is held: $held. Zoom: ${String.format(java.util.Locale.US, "%.1f", zoom)}×. " +
            "The phone's own detector thinks it sees: ${if (things.isEmpty()) "nothing it recognises" else describe(things)} (it can be wrong)."
    }

    var lastReqAt by remember { mutableLongStateOf(0L) }

    fun askPhotographer() {
        if (busy) return
        val bmp = previewView.bitmap ?: return
        val seenNow = things
        val tiltNow = tilt
        val ctx = phoneContext() + " What should this person do to take a better photo of this?"
        busy = true
        askError = null
        lastReqAt = SystemClock.elapsedRealtime()
        scope.launch {
            val r = withContext(Dispatchers.IO) { Photographer.ask(bmp, ctx) }
            busy = false
            when (r) {
                is Reply.Ok -> {
                    val subject = matchSubject(r.value.subjectBox, seenNow)
                    subjectBox = subject?.box ?: r.value.subjectBox
                    streak.clear(); liveOk.clear(); checkError = null
                    val trusted = r.value.moves.map { liveCheck(it, tiltNow, subject) != true }
                    coach = Coach(r.value, subject?.id, liveTrusted = trusted)
                }
                is Reply.Failed -> {
                    // No photographer (offline, busy, no key): the quick on-phone tips carry on instead.
                    if (askError == null) toast = "The photographer isn't available right now, so here are quick tips."
                    askError = r.message
                }
            }
        }
    }

    fun checkShot() {
        val c = coach ?: return
        if (busy) return
        val bmp = previewView.bitmap ?: return
        val ctx = phoneContext()
        busy = true
        checkError = null
        lastReqAt = SystemClock.elapsedRealtime()
        scope.launch {
            val r = withContext(Dispatchers.IO) { Photographer.check(bmp, c.advice, ctx) }
            busy = false
            when (r) {
                is Reply.Ok -> coach = coach?.let { now ->
                    now.copy(
                        checked = now.checked.mapIndexed { i, was -> was || r.value.done.getOrElse(i) { false } },
                        notes = r.value.notes, next = r.value.next, ready = r.value.ready,
                    )
                }
                is Reply.Failed -> checkError = r.message
            }
        }
    }

    fun closeCoach() { coach = null; checkError = null; streak.clear(); liveOk.clear() }

    // The photographer's subject, followed live: the same tracked thing, or whatever overlaps its last place most.
    val coachSubject = coach?.let { c -> things.firstOrNull { it.id != null && it.id == c.subjectId } ?: subjectBox?.let { matchSubject(it, things) } }
    LaunchedEffect(frame) {
        val c = coach ?: return@LaunchedEffect
        coachSubject?.let { subjectBox = it.box }
        // A step turns done after 2 good frames in a row and back after 3 bad ones, so it doesn't flicker.
        c.advice.moves.forEachIndexed { i, m ->
            val ok = liveCheck(m, tilt, coachSubject) ?: return@forEachIndexed
            val prev = streak[i] ?: 0
            val next = if (ok) (if (prev > 0) prev + 1 else 1) else (if (prev < 0) prev - 1 else -1)
            streak[i] = next
            if (next >= 2) liveOk[i] = true else if (next <= -3) liveOk[i] = false
        }
    }
    val steps = coach?.let { c ->
        c.advice.moves.mapIndexed { i, m ->
            val measurable = when (m.check) { CheckBy.Angle -> tilt != Tilt.Unknown; CheckBy.Frame -> coachSubject != null; CheckBy.Other -> false }
            val live = measurable && c.liveTrusted.getOrElse(i) { true }
            StepView(m, done = if (live) liveOk[i] == true else c.checked.getOrElse(i) { false }, live = live, note = c.notes.getOrElse(i) { "" })
        }
    }.orEmpty()
    val ringStep = steps.indexOfFirst { !it.done && it.live && it.move.check == CheckBy.Frame }
    val ringMove = steps.getOrNull(ringStep)?.move
    val allDone = coach != null && (coach!!.ready || (steps.isNotEmpty() && steps.all { it.done }))
    // Set when the person closes the card (✕): quick tips for the rest of this scene.
    var closedFor by remember { mutableLongStateOf(-1L) }
    val auto = state.tipsOn && Photographer.hasKey
    /** The photographer owns the screen: its card is up, or it's about to be (asking, no failure, not closed). */
    val photographerOn = coach != null || (auto && askError == null && closedFor != scanStart && !dark)
    SideEffect { planUp.value = coach != null }

    // ---- Automatic (user decisions, 2026-10-06): no buttons. The photographer is asked as soon as the view has been
    // up for 0.8s (not after the 3-second look: too slow). Steps the phone can't measure are re-checked by themselves
    // once the person has changed something and holds still, spaced out to stay inside Gemini's free limits.
    // A new look (scene changed) clears the old plan and asks again. ----
    var askedFor by remember { mutableLongStateOf(-1L) }
    var scene by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sceneChangedAt by remember { mutableLongStateOf(0L) }
    var sceneAtCheck by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(pick) { if (pick == null) { closeCoach(); askError = null } }
    LaunchedEffect(frame) {
        // A rough fingerprint of what's in view: count, where the subject is, brightness, phone angle.
        val f = frame ?: return@LaunchedEffect
        val s = coachSubject
        val next = listOf(f.things.size, s?.let { (it.cx * 8).toInt() } ?: -1, s?.let { (it.cy * 8).toInt() } ?: -1, (f.meanY / 15).toInt(), (tilt.offFlatDeg / 12).toInt())
        if (next != scene) { scene = next; sceneChangedAt = SystemClock.elapsedRealtime() }
    }
    LaunchedEffect(now) {
        if (!auto || busy || dark) return@LaunchedEffect
        val c = coach
        if (c == null) {
            if (closedFor == scanStart || frame == null) return@LaunchedEffect
            val firstAsk = askedFor != scanStart && now - scanStart >= 800
            val retry = askError != null && now - lastReqAt > 30_000
            if (firstAsk || retry) { askedFor = scanStart; askPhotographer() }
            return@LaunchedEffect
        }
        if (allDone) return@LaunchedEffect
        if (steps.none { !it.done && !it.live }) return@LaunchedEffect
        val sinceReq = now - lastReqAt
        val settled = now - sceneChangedAt >= 1500
        if (sinceReq >= 10_000 && scene != sceneAtCheck && settled) {
            sceneAtCheck = scene
            checkShot()
        }
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
            ThingsLayer(if (pick != null) things.take(slots) else things, marksVisible && !photographerOn, Modifier.fillMaxSize())
            GuideLayer(guide, slots, guideAlignment, points, guideVisible && !photographerOn, Modifier.fillMaxSize())
            if (!photographerOn) EdgeLayer(shown.main, shown.edge, Modifier.fillMaxSize())
            else if (allDone) EdgeLayer(coachSubject, EdgeState.Right, Modifier.fillMaxSize())
            CoachLayer(
                target = ringMove?.let { P(it.targetX ?: 0.5f, it.targetY ?: 0.5f) },
                subject = coachSubject?.let { P(it.cx, it.cy) },
                hit = ringStep >= 0 && liveOk[ringStep] == true,
                modifier = Modifier.fillMaxSize(),
            )
            if (!photographerOn) TipCapsule(shown, ::notRight, Modifier.align(Alignment.TopCenter).padding(top = CPSpace.S2, start = CPSpace.S3, end = CPSpace.S3))
            NoteChip(if (dark) shown.phone else null, Modifier.align(Alignment.TopCenter).padding(top = CPSpace.S2))
            val top = Modifier.align(Alignment.TopCenter).padding(top = CPSpace.S2, start = CPSpace.S3, end = CPSpace.S3)
            InfoCapsule(
                visible = scanning && !dark && !photographerOn && !auto,
                line1 = if (count == 0 && now - scanStart > 1500) "Point at what you want to shoot." else "Looking at what's here…",
                line2 = if (count == 0) "I'll mark everything I recognise." else "Hold still. Found ${describe(things)} so far.",
                progress = scanProgress,
                modifier = top,
            )
            InfoCapsule(
                visible = explaining && !dark && !photographerOn && !auto,
                line1 = pick?.let { "I see ${it.seen}. Let's use ${it.guide.label}." } ?: "",
                line2 = pick?.why ?: "",
                modifier = top,
            )
            GuideChip(pick, guideVisible && !explaining && !photographerOn, ::lookAgain, Modifier.align(Alignment.BottomCenter).padding(bottom = CPSpace.S2))
            ZoomChip(zoom, now - zoomShownAt < 900, Modifier.align(Alignment.Center))
            Box(Modifier.fillMaxSize().alpha(flash.value).background(CP.OnDark))
            Box(Modifier.fillMaxSize().alpha(cover.value).background(CP.CameraBar))
            if (unavailable) {
                Box(Modifier.fillMaxSize().background(CP.CameraBar).padding(CPSpace.S4), contentAlignment = Alignment.Center) {
                    Text("Camera isn't available right now. Close other apps using it and try again.", style = CPType.Body, color = CP.OnDark, textAlign = TextAlign.Center)
                }
            }
            // One card, one place: "Finding the best shot…" while it works, then the steps.
            val bottom = Modifier.align(Alignment.BottomCenter).padding(CPSpace.S2)
            if (coach == null && photographerOn) FindingCard(bottom)
            coach?.let { c ->
                CoachCard(
                    steps = steps, next = c.next, ready = allDone, checking = busy, error = checkError,
                    onClose = { closeCoach(); closedFor = scanStart }, modifier = bottom,
                )
            }
            toast?.let { GlassToast(it, Modifier.align(Alignment.BottomCenter).padding(bottom = CPSpace.S3)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
}

@Composable
fun GlassToast(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = CPSpace.S3).clip(CPShape.Pill).background(CP.Glass).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, style = CPType.CaptionMedium, color = CP.OnDark, textAlign = TextAlign.Center)
    }
}

/** Before a guide is picked, the only thing worth saying is "too dark". */
private fun darkOnly(frame: FrameResult?): TipDecision =
    if (frame != null && frame.meanY < Thresholds.TOO_DARK) TipDecision("dark", TipKind.Note, "Too dark for me to see. Try more light, or just shoot.")
    else TipDecision.NONE
