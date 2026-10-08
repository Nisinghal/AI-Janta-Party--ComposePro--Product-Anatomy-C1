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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.CameraInfo
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import android.hardware.camera2.CameraCharacteristics
import java.util.concurrent.TimeUnit
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import com.composepro.app.ai.frameSlots
import com.composepro.app.ai.Reply
import com.composepro.app.ai.liveCheck
import com.composepro.app.ai.matchSubject
import android.graphics.RectF
import com.composepro.app.ai.Photographer
import com.composepro.app.camera.Tilt
import com.composepro.app.camera.FrameAnalyzer
import com.composepro.app.camera.FrameResult
import com.composepro.app.camera.Thing
import com.composepro.app.camera.Tracker
import com.composepro.app.camera.Gray
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

    // ---- Lenses: the main camera, plus the phone's wide lens for 0.5× when it has one (group feedback 2026-10-07:
    // "no 0.5×, 1×, 2×"). And what's saved = what's on screen: all three uses share the preview's own viewport, so the
    // photo is cropped exactly like the preview ("photos in the gallery show different framing"). ----
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var wideSelector by remember { mutableStateOf<CameraSelector?>(null) }
    var wideRatio by remember { mutableFloatStateOf(0.5f) }
    var onWide by remember { mutableStateOf(false) }
    suspend fun bindCamera(wide: Boolean) {
        val p = provider ?: return
        val preview = Preview.Builder().setResolutionSelector(fourByThree).build().also { it.surfaceProvider = previewView.surfaceProvider }
        var vp = previewView.viewPort
        repeat(40) { if (vp == null) { delay(25); vp = previewView.viewPort } }
        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture).addUseCase(imageAnalysis)
            .apply { vp?.let { setViewPort(it) } }.build()
        p.unbindAll()
        val useWide = wide && wideSelector != null
        camera = p.bindToLifecycle(lifecycleOwner, if (useWide) wideSelector!! else CameraSelector.DEFAULT_BACK_CAMERA, group)
        onWide = useWide
        zoom = 1f
    }
    LaunchedEffect(Unit) {
        try {
            val p = ProcessCameraProvider.awaitInstance(context)
            provider = p
            findWideLens(p)?.let { (sel, ratio) -> wideSelector = sel; wideRatio = ratio }
            bindCamera(false)
            unavailable = false
            cover.animateTo(0f, tween(350))
        } catch (e: Exception) {
            unavailable = true
        }
    }
    val shownZoom = if (onWide) wideRatio * zoom else zoom
    val zoomStops = listOfNotNull(
        if (wideSelector != null) wideRatio else (camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f).takeIf { it <= 0.7f },
        1f,
        if ((camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f) >= 2f) 2f else null,
    )
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Photographer.warmUp() } }
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

    // The line under the photo on Review. Set further down, once the photographer's steps are known.
    val reviewLine = remember { mutableStateOf<String?>(null) }

    fun takePhoto() {
        if (taking || unavailable) return
        taking = true
        val reminder = reviewLine.value
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
    // Follows the photographer's subject by how it looks, for anything the detector has no name for.
    val tracker = remember { Tracker() }
    // Rough look of the view when the plan arrived (brightness, phone angle) and when its subject was last seen.
    var planScene by remember { mutableStateOf<List<Int>>(emptyList()) }
    var planAt by remember { mutableLongStateOf(0L) }
    var lostSince by remember { mutableLongStateOf(0L) }
    var trackedBox by remember { mutableStateOf<RectF?>(null) }
    val streak = remember { mutableStateMapOf<Int, Int>() }
    val liveOk = remember { mutableStateMapOf<Int, Boolean>() }
    // Steps the person ticked (or unticked) themselves ("I can't go any lower than this", classmate S, 2026-10-06).
    val manualDone = remember { mutableStateMapOf<Int, Boolean>() }
    // Once a step has been done it stays done (group test 2026-10-07: "both steps never complete". Tilting for one
    // step moved the subject off the ring of the other, so a finished step unticked itself and they chased each other).
    val doneOnce = remember { mutableStateMapOf<Int, Boolean>() }
    // Zoom the tracker last knew, so a zoom tap moves its box along with the picture.
    var trackZoom by remember { mutableFloatStateOf(1f) }
    var lastRefind by remember { mutableLongStateOf(0L) }
    // What the view looked like when the plan arrived, to notice when they've turned to something else.
    var planGray by remember { mutableStateOf<Gray?>(null) }
    // Looks like they've turned to something else: the card suggests ↻ (the steps stay until they tap it).
    var newScene by remember { mutableStateOf(false) }
    // When the phone has been held still (frame-to-frame change small) — following a step means moving.
    var lastGray by remember { mutableStateOf<Gray?>(null) }
    var steadySince by remember { mutableLongStateOf(0L) }
    LaunchedEffect(frame) {
        val g = frame?.gray ?: return@LaunchedEffect
        val t = SystemClock.elapsedRealtime()
        if (differs(g, lastGray) < 10f) { if (steadySince == 0L) steadySince = t } else steadySince = 0L
        lastGray = g
    }

    fun phoneContext(): String {
        val held = when {
            tilt == Tilt.Unknown -> "unknown"
            tilt.flat -> "pointing straight down at a table"
            tilt.offFlatDeg < 60f -> "tilted down, about ${tilt.offFlatDeg.toInt()}° from pointing straight down"
            else -> "upright, at about eye level"
        }
        val stops = zoomStops.joinToString(", ") { if (it < 1f) String.format(java.util.Locale.US, "%.1f×", it) else "${it.toInt()}×" }
        return "How the phone is held: $held. Zoom now: ${String.format(java.util.Locale.US, "%.1f", shownZoom)}×. Zoom buttons on this phone: $stops. " +
            "The phone's own detector thinks it sees: ${if (things.isEmpty()) "nothing it recognises" else describe(things)} (it can be wrong)."
    }

    var lastReqAt by remember { mutableLongStateOf(0L) }

    fun askPhotographer() {
        if (busy) return
        val bmp = previewView.bitmap ?: return
        val seenNow = things
        val tiltNow = tilt
        val grayAtAsk = frame?.gray
        val ctx = phoneContext() + " What should this person do to take a better photo of this?"
        busy = true
        askError = null
        lastReqAt = SystemClock.elapsedRealtime()
        scope.launch {
            val r = withContext(Dispatchers.IO) { Photographer.ask(bmp, ctx) }
            busy = false
            when (r) {
                is Reply.Ok -> {
                    val subject = r.value.subjectBox?.let { b -> matchSubject(b, seenNow) }
                    subjectBox = subject?.box ?: r.value.subjectBox
                    // Start following it: remember how it looked when asked, and find it in the latest frame.
                    tracker.stop(); trackedBox = null
                    val b = r.value.subjectBox
                    val nowGray = frame?.gray
                    if (b != null && grayAtAsk != null && nowGray != null) {
                        val ok = withContext(Dispatchers.Default) { tracker.start(grayAtAsk, b, nowGray) }
                        trackedBox = if (ok) tracker.box else null
                    }
                    android.util.Log.i(
                        "ComposePro",
                        "Subject: photographer box=$b, detector=${subject?.category}#${subject?.id} ${subject?.box}, " +
                            "tracker=${if (trackedBox != null) "found at $trackedBox" else "lost"} (match ${"%.2f".format(tracker.lastScore)}), phone saw ${seenNow.map { "${it.category}${it.box}" }}",
                    )
                    streak.clear(); liveOk.clear(); doneOnce.clear(); checkError = null; trackZoom = shownZoom
                    val trusted = r.value.moves.map { it.check != CheckBy.Angle || liveCheck(it, tiltNow, subject, shownZoom) != true }
                    planScene = coarseScene(frame, tilt); planAt = SystemClock.elapsedRealtime(); lostSince = 0L
                    planGray = frame?.gray; manualDone.clear(); newScene = false
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
            val already = c.advice.moves.indices.map { doneOnce[it] == true || manualDone[it] == true }
            val r = withContext(Dispatchers.IO) { Photographer.check(bmp, c.advice, ctx, already) }
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

    fun closeCoach() { coach = null; checkError = null; streak.clear(); liveOk.clear(); doneOnce.clear(); tracker.stop(); trackedBox = null }

    // Zoom taps and pinches: move the tracked box with the picture instead of losing the subject.
    LaunchedEffect(shownZoom) {
        if (tracker.hasSubject && trackZoom > 0f) { tracker.zoomBy(shownZoom / trackZoom); trackedBox = tracker.box }
        trackZoom = shownZoom
    }

    // The photographer's subject, followed live: the same tracked thing, or whatever overlaps its last place most.
    val coachSubject = coach?.let { c ->
        things.firstOrNull { it.id != null && it.id == c.subjectId }
            ?: trackedBox?.let { Thing(null, it, c.advice.subject) }
    }
    LaunchedEffect(frame) {
        val c = coach ?: return@LaunchedEffect
        frame?.gray?.let { g ->
            if (tracker.locked) {
                tracker.update(g); trackedBox = tracker.box
                if (!tracker.locked) android.util.Log.i("ComposePro", "Tracker lost the subject (match ${"%.2f".format(tracker.lastScore)})")
            } else if (tracker.hasSubject && SystemClock.elapsedRealtime() - lastRefind > 700) {
                // Lost: look for it again about once a second, so its steps can be measured again when it's back.
                lastRefind = SystemClock.elapsedRealtime()
                if (tracker.refind(g)) {
                    trackedBox = tracker.box
                    android.util.Log.i("ComposePro", "Tracker found the subject again (match ${"%.2f".format(tracker.lastScore)})")
                }
            }
        }
        val t = SystemClock.elapsedRealtime()
        if (coachSubject == null) { if (lostSince == 0L) lostSince = t } else lostSince = 0L
        coachSubject?.let { subjectBox = it.box }
        // A step turns done after 2 good frames in a row and back after 3 bad ones, so it doesn't flicker.
        c.advice.moves.forEachIndexed { i, m ->
            val ok = liveCheck(m, tilt, coachSubject, shownZoom) ?: return@forEachIndexed
            val prev = streak[i] ?: 0
            val next = if (ok) (if (prev > 0) prev + 1 else 1) else (if (prev < 0) prev - 1 else -1)
            streak[i] = next
            if (next >= 2) liveOk[i] = true else if (next <= -3) liveOk[i] = false
            // Not for a step whose phone check already passed when it was given (it measures the wrong thing there).
            if (liveOk[i] == true && c.liveTrusted.getOrElse(i) { true } && doneOnce[i] != true) {
                doneOnce[i] = true
                android.util.Log.i("ComposePro", "Step ${i + 1} done (${m.check}): ${m.action}")
            }
        }
    }
    val steps = coach?.let { c ->
        c.advice.moves.mapIndexed { i, m ->
            val measurable = when (m.check) { CheckBy.Angle -> tilt != Tilt.Unknown; CheckBy.Frame -> coachSubject != null; CheckBy.Zoom -> true; CheckBy.Other -> false }
            val live = measurable && c.liveTrusted.getOrElse(i) { true }
            // Done by the phone's own check or by the photographer's look, and then it stays done; a tap overrides.
            val auto = doneOnce[i] == true || c.checked.getOrElse(i) { false }
            StepView(m, done = manualDone[i] ?: auto, live = live, note = c.notes.getOrElse(i) { "" })
        }
    }.orEmpty()
    // The ring shows for the first open position step even while the subject is momentarily lost (no dot then).
    val ringStep = steps.indexOfFirst { !it.done }.takeIf { it >= 0 && steps[it].move.check == CheckBy.Frame } ?: -1
    val ringMove = steps.getOrNull(ringStep)?.move
    val allDone = coach != null && (coach!!.ready || (steps.isNotEmpty() && steps.all { it.done }))
    // Set when the person closes the card (✕): quick tips for the rest of this scene.
    var closedFor by remember { mutableLongStateOf(-1L) }
    val auto = state.tipsOn && Photographer.hasKey
    /** The photographer owns the screen: its card is up, or it's about to be (asking, no failure, not closed). */
    val photographerOn = coach != null || (auto && askError == null && closedFor != scanStart && !dark)
    SideEffect { planUp.value = coach != null }
    // Group feedback (2026-10-07): Review said "The tip was: turn so the light falls on it" right after the
    // photographer's card said "All done". That was a hidden quick tip from the older tip system. While the
    // photographer is on, Review now reports its steps; a quick tip is only mentioned when it was actually on screen.
    SideEffect {
        reviewLine.value = when {
            coach != null && steps.isNotEmpty() ->
                if (allDone) "You did all ${steps.size} steps." else "${steps.count { it.done }} of ${steps.size} steps done."
            photographerOn -> null
            shown.isTip && state.tipsOn -> "The tip was: ${shown.remind}"
            else -> null
        }
    }

    // ---- Automatic (user decisions, 2026-10-06): no buttons. The photographer is asked as soon as the view has been
    // up for 0.8s (not after the 3-second look: too slow). Steps the phone can't measure are re-checked by themselves
    // once the person has changed something and holds still, spaced out to stay inside Gemini's free limits.
    // A new look (scene changed) clears the old plan and asks again. ----
    var askedFor by remember { mutableLongStateOf(-1L) }
    var scene by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sceneChangedAt by remember { mutableLongStateOf(0L) }
    var sceneAtCheck by remember { mutableStateOf<List<Int>>(emptyList()) }
    var grayAtCheck by remember { mutableStateOf<Gray?>(null) }
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
        // Turned to something else: the subject has been gone 2.5 s and the view looks clearly different from when the
        // plan came (or brighter/darker, or at another angle). Old steps for the old subject are dropped and new ones
        // asked for (classmate N: "if you change focus to another subject it still gives suggestions for the old one").
        // Group feedback 2026-10-07: while following a step (tilting, shifting) the steps were thrown away and replaced,
        // "1 of 3 done" dropping to "0 of 2". Now it only starts over when they've clearly settled on something else:
        // subject gone 5 s, the phone held still for 1.5 s, the view very different, and the plan at least 8 s old.
        // Otherwise the ↻ on the card does it.
        val looksDifferent = coarseScene(frame, tilt) != planScene || differs(frame?.gray, planGray) > 40f
        val heldStill = steadySince != 0L && now - steadySince > 1_500
        // Group feedback round 3 (2026-10-07): still "changes the instructions sometimes", and any change is a problem.
        // So the steps are never replaced by the app any more: when it looks like a new scene it only suggests ↻.
        newScene = lostSince != 0L && now - lostSince > 5_000 && now - planAt > 8_000 && heldStill && looksDifferent
        if (allDone) return@LaunchedEffect
        if (steps.all { it.done }) return@LaunchedEffect
        // Group test 2026-10-07: open steps waited for a change in a rough fingerprint (count, where, brightness, angle),
        // so moving a cable or tilting slightly never got looked at again. Now any visible change counts too.
        val sinceReq = now - lastReqAt
        val settled = now - sceneChangedAt >= 1500 || (steadySince != 0L && now - steadySince >= 1500)
        val changed = scene != sceneAtCheck || differs(frame?.gray, grayAtCheck) > 12f
        if (sinceReq >= 10_000 && changed && settled) {
            sceneAtCheck = scene; grayAtCheck = frame?.gray
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

    // ---- Camera basics like any camera app (classmate feedback 2026-10-06: "it doesn't zoom in, zoom out or focus
    // like a simple camera"). Pinch to zoom, tap to focus, 1×/2× buttons. The gestures sit on a layer ABOVE the
    // preview: the preview view used to take the touches itself, so the old pinch never fired. ----
    fun setZoomNow(z: Float) {
        val cam = camera ?: return
        val zs = cam.cameraInfo.zoomState.value
        zoom = z.coerceIn(zs?.minZoomRatio ?: 1f, zs?.maxZoomRatio ?: 1f)
        cam.cameraControl.setZoomRatio(zoom)
    }
    fun setZoom(z: Float) {
        val cam = camera ?: return
        val zs = cam.cameraInfo.zoomState.value
        zoom = z.coerceIn(zs?.minZoomRatio ?: 1f, zs?.maxZoomRatio ?: 1f)
        cam.cameraControl.setZoomRatio(zoom)
        zoomShownAt = SystemClock.elapsedRealtime()
    }
    fun pickZoom(z: Float) {
        scope.launch {
            when {
                z < 0.95f && wideSelector != null -> if (!onWide) bindCamera(true)
                onWide -> { bindCamera(false); setZoomNow(z) }
                else -> setZoomNow(z)
            }
            zoomShownAt = SystemClock.elapsedRealtime()
        }
    }
    // Flash for the photo: Auto → On → Off, like the phone's own camera (user request, 2026-10-06).
    var flashSetting by remember { mutableStateOf(FlashSetting.Auto) }
    LaunchedEffect(flashSetting) { imageCapture.flashMode = flashSetting.captureMode }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusShownAt by remember { mutableLongStateOf(0L) }
    fun focus(at: Offset) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(at.x, at.y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
        focusAt = at
        focusShownAt = SystemClock.elapsedRealtime()
    }
    val touchLayer = Modifier
        .pointerInput(camera) { detectTransformGestures { _, _, zoomChange, _ -> if (zoomChange != 1f) setZoom(zoom * zoomChange) } }
        .pointerInput(camera) { detectTapGestures(onTap = { focus(it) }) }

    // ---- Auto shot (user request 2026-10-08: "the user just holds, the camera adjusts and captures; as an option").
    // The camera does what it can itself (a zoom step is applied, focus and exposure go to the subject); once the steps
    // are done and the phone is held still, a ring fills round the shutter and the photo is taken. Moving cancels it. ----
    val openedAt = remember { SystemClock.elapsedRealtime() }
    var zoomedFor by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(coach, state.autoShot) {
        val c = coach ?: return@LaunchedEffect
        if (!state.autoShot || zoomedFor === c.advice) return@LaunchedEffect
        zoomedFor = c.advice
        val z = c.advice.moves.firstOrNull { it.check == CheckBy.Zoom }?.zoom ?: return@LaunchedEffect
        if (kotlin.math.abs(shownZoom - z) > 0.05f) pickZoom(z)
    }
    val shotReady = when {
        unavailable || taking || dark -> false
        coach != null -> allDone
        photographerOn -> false            // the steps are still on their way
        !state.tipsOn -> true
        else -> shown.edge == EdgeState.Right
    }
    val heldForShot = steadySince != 0L && now - steadySince >= 500
    val armed = state.autoShot && shotReady && heldForShot && now - openedAt > 2_000
    var countFrom by remember { mutableLongStateOf(0L) }
    LaunchedEffect(armed) {
        if (!armed) { countFrom = 0L; return@LaunchedEffect }
        countFrom = SystemClock.elapsedRealtime()
        (coachSubject ?: shown.main)?.let { s -> focus(Offset(s.cx * previewView.width, s.cy * previewView.height)) }
        delay(AUTO_SHOT_MS)
        countFrom = 0L
        takePhoto()
    }
    val autoProgress = if (countFrom == 0L) 0f else ((now - countFrom).toFloat() / AUTO_SHOT_MS).coerceIn(0f, 1f)

    // Full-screen camera (user decision 2026-10-07: "remove the space, just keep the buttons, tips and all"). The picture
    // fills the screen and everything floats on top. The photo is cut to this same view, so what's on screen, white
    // space included, is what's saved.
    Box(Modifier.fillMaxSize().background(CP.CameraBar)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().then(touchLayer))
        GridLayer(state.tipsOn && !dark, Modifier.fillMaxSize())
        ThingsLayer(if (pick != null) things.take(slots) else things, marksVisible && !photographerOn, Modifier.fillMaxSize())
        GuideLayer(guide, slots, guideAlignment, points, guideVisible && !photographerOn, Modifier.fillMaxSize())
        // The photographer's composition frame (thirds, centre… from the reference images), drawn faintly.
        coach?.advice?.frame?.let { f -> GuideLayer(f, frameSlots(f), null, emptyList(), true, Modifier.fillMaxSize()) }
        if (!photographerOn) EdgeLayer(shown.main, shown.edge, Modifier.fillMaxSize())
        else if (allDone) EdgeLayer(coachSubject, EdgeState.Right, Modifier.fillMaxSize())
        CoachLayer(
            target = ringMove?.let { P(it.targetX ?: 0.5f, it.targetY ?: 0.5f) },
            subject = coachSubject?.let { P(it.cx, it.cy) },
            subjectBox = if (ringMove != null) coachSubject?.box else null,
            targetSize = ringMove?.size,
            // The ring goes green exactly when its step does, so the two never disagree.
            hit = steps.getOrNull(ringStep)?.done == true,
            modifier = Modifier.fillMaxSize(),
        )
        ZoomChip(shownZoom, now - zoomShownAt < 900, Modifier.align(Alignment.Center))
        FocusRing(focusAt, now - focusShownAt < 1200)
        Box(Modifier.fillMaxSize().alpha(flash.value).background(CP.OnDark))
        Box(Modifier.fillMaxSize().alpha(cover.value).background(CP.CameraBar))
        if (unavailable) {
            Box(Modifier.fillMaxSize().background(CP.CameraBar).padding(CPSpace.S4), contentAlignment = Alignment.Center) {
                Text("Camera isn't available right now. Close other apps using it and try again.", style = CPType.Body, color = CP.OnDark, textAlign = TextAlign.Center)
            }
        }

        // Top: notes and quick tips, under the status bar
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = CPSpace.S2, start = CPSpace.S3, end = CPSpace.S3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!photographerOn) TipCapsule(shown, ::notRight)
            NoteChip(if (dark) shown.phone else null)
            InfoCapsule(
                visible = scanning && !dark && !photographerOn && !auto,
                line1 = if (count == 0 && now - scanStart > 1500) "Point at what you want to shoot." else "Looking at what's here…",
                line2 = if (count == 0) "I'll mark everything I recognise." else "Hold still. Found ${describe(things)} so far.",
                progress = scanProgress,
            )
            InfoCapsule(
                visible = explaining && !dark && !photographerOn && !auto,
                line1 = pick?.let { "I see ${it.seen}. Let's use ${it.guide.label}." } ?: "",
                line2 = pick?.why ?: "",
            )
        }

        // Bottom: a soft dark fade so the controls read over any picture, then flash + zoom, the steps, the shutter row
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .navigationBarsPadding().padding(top = CPSpace.S4, bottom = CPSpace.S3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            toast?.let { GlassToast(it, Modifier.padding(bottom = CPSpace.S2)) }
            if (autoProgress > 0f && toast == null) GlassToast("Hold still…", Modifier.padding(bottom = CPSpace.S2))
            GuideChip(pick, guideVisible && !explaining && !photographerOn, ::lookAgain, Modifier.padding(bottom = CPSpace.S2))
            Row(
                Modifier.fillMaxWidth().padding(start = CPSpace.S2, end = CPSpace.S2, bottom = CPSpace.S2),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(CPSpace.S1), verticalAlignment = Alignment.CenterVertically) {
                    if (camera?.cameraInfo?.hasFlashUnit() == true) FlashButton(flashSetting, onClick = { flashSetting = flashSetting.next() })
                    AutoShotButton(state.autoShot, onClick = {
                        state.switchAutoShot(!state.autoShot)
                        toast = if (state.autoShot) "Auto shot on. Do the steps and hold still; it takes the photo." else "Auto shot off."
                    })
                }
                ZoomButtons(current = shownZoom, stops = zoomStops, onPick = ::pickZoom)
            }
            val cardSpot = Modifier.padding(start = CPSpace.S2, end = CPSpace.S2, bottom = CPSpace.S3)
            if (coach == null && photographerOn) FindingCard(cardSpot)
            coach?.let {
                CoachCard(
                    frame = it.advice.frame?.label, frameWhy = it.advice.frameWhy,
                    steps = steps, ready = allDone, checking = busy, error = checkError,
                    onClose = { closeCoach(); closedFor = scanStart },
                    onTick = { i -> manualDone[i] = !(steps.getOrNull(i)?.done ?: false) },
                    onNewSteps = { closeCoach(); askedFor = -1L },
                    newScene = newScene,
                    modifier = cardSpot,
                )
            }
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
                ) {
                    Box(Modifier.size(58.dp).clip(CPShape.Pill).background(CP.OnDark))
                    if (autoProgress > 0f) AutoShotRing(autoProgress, Modifier.size(72.dp))
                }
                Box(
                    Modifier.size(CPSpace.Tap).clip(CPShape.Pill).background(CP.Glass)
                        .clickable(role = Role.Button) { state.screen = Screen.Settings },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Settings, "Settings", tint = CP.OnDark, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

/** How long the phone must stay still, steps done, before Auto shot takes the photo. */
private const val AUTO_SHOT_MS = 1500L

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

/** Brightness and phone angle in coarse steps: changes only when the person points somewhere clearly different. */
private fun coarseScene(frame: FrameResult?, tilt: Tilt): List<Int> =
    listOf(((frame?.meanY ?: 0f) / 30).toInt(), (tilt.offFlatDeg / 25).toInt())

/** Average difference (0–255) between two greyscale frames: small while following steps, large on a new scene. */
private fun differs(a: Gray?, b: Gray?): Float {
    if (a == null || b == null || a.px.size != b.px.size) return 0f
    var sum = 0L
    for (i in a.px.indices step 2) sum += kotlin.math.abs((a.px[i].toInt() and 0xFF) - (b.px[i].toInt() and 0xFF))
    return sum.toFloat() / (a.px.size / 2)
}

/**
 * The phone's wide (0.5×-style) back lens, if it shows one to apps, and how much wider it sees than the main camera.
 * Compared by field of view (sensor width ÷ focal length), so a low-res macro lens isn't mistaken for it.
 */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun findWideLens(p: ProcessCameraProvider): Pair<CameraSelector, Float>? = try {
    fun fov(info: CameraInfo): Float? {
        val c = Camera2CameraInfo.from(info)
        val f = c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: return null
        val w = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: return null
        return w / f
    }
    val backs = p.availableCameraInfos.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
    val main = backs.firstOrNull()
    val mainFov = main?.let { fov(it) }
    android.util.Log.i("ComposePro", "Back cameras: ${backs.map { fov(it) }} (main first)")
    if (main == null || mainFov == null) null
    else backs.drop(1).mapNotNull { i -> fov(i)?.let { i to it } }.filter { it.second > mainFov * 1.3f }.maxByOrNull { it.second }
        ?.let { (info, f) -> info.cameraSelector to (Math.round(mainFov / f * 10f) / 10f) }
} catch (e: Exception) {
    android.util.Log.w("ComposePro", "Couldn't look for a wide lens", e)
    null
}
