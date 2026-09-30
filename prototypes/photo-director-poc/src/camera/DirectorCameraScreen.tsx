"use client";
import { useCallback, useContext, useEffect, useRef, useState } from "react";
import { FilesetResolver, ObjectDetector } from "@mediapipe/tasks-vision";
import { Bug, Camera as CameraIcon, Crop, RefreshCw, X, Sparkles } from "lucide-react";
import { getCompositionGuidance, inferFraming } from "../composition/compositionEngine";
import { compositionConfig as config } from "../composition/compositionConfig";
import { smoothSubject, stabilizeGuidance, type StabilizerState } from "../composition/guidanceStabilizer";
import { normalizeDetection } from "../vision/normalizeDetection";
import { assessScene, sceneGuidance } from "../vision/sceneAnalysis";
import type { BoundingBox, CompositionGuidance, DetectedSubject, FramingMode, SceneObservation } from "../types/composition";
import type { SceneAssessment } from "../types/scene";
import GuidancePill from "../components/GuidancePill";
import DebugOverlay from "../overlays/DebugOverlay";
import PhotoResultScreen from "../results/PhotoResultScreen";
import SceneCues from "../overlays/SceneCues";
import PresetCarousel from "../components/PresetCarousel";
import SubjectTargetZone from "../overlays/SubjectTargetZone";
import { evaluateComposition } from "../engines/compositionEngine";
import { CompositionContext, CompositionProvider } from "../context/CompositionContext";
import { useCompositionAnalysis } from "../hooks/useCompositionAnalysis";
import { useAutoCrop, type CropRect } from "../hooks/useAutoCrop";

const emptyGuidance: CompositionGuidance = { type: "NO_SUBJECT", message: "Point the camera at food", severity: 1 };
const emptyScene: SceneAssessment = { brightness:.5, highlightClip:0, shadowClip:0, edgeDensity:0, subjectContrast:.25, lightTone:"good", backgroundTone:"good", separationTone:"good", objects:[] };

// ─── Focus & Exposure Helper ────────────────────────────────────────────────
// Desktop webcams often have limited WebRTC constraint support. We try every
// avenue the spec provides and gracefully degrade.
async function enforceCameraSettings(track: MediaStreamTrack) {
  try {
    const caps: any = track.getCapabilities?.() || {};
    const advanced: Record<string, any> = {};

    // Focus
    if (caps.focusMode) {
      if (Array.isArray(caps.focusMode) && caps.focusMode.includes("continuous")) {
        advanced.focusMode = "continuous";
      } else if (Array.isArray(caps.focusMode) && caps.focusMode.length > 0) {
        // Pick last mode (typically the most "auto" one)
        advanced.focusMode = caps.focusMode[caps.focusMode.length - 1];
      }
    }

    // Exposure
    if (caps.exposureMode) {
      if (Array.isArray(caps.exposureMode) && caps.exposureMode.includes("continuous")) {
        advanced.exposureMode = "continuous";
      } else if (Array.isArray(caps.exposureMode) && caps.exposureMode.length > 0) {
        advanced.exposureMode = caps.exposureMode[caps.exposureMode.length - 1];
      }
    }

    // White balance
    if (caps.whiteBalanceMode) {
      if (Array.isArray(caps.whiteBalanceMode) && caps.whiteBalanceMode.includes("continuous")) {
        advanced.whiteBalanceMode = "continuous";
      }
    }

    // Brightness – push it up slightly if we can (helps dim webcams)
    if (caps.brightness && typeof caps.brightness.max === "number") {
      const mid = ((caps.brightness.max + caps.brightness.min) / 2);
      // Nudge ~10% above center
      advanced.brightness = Math.min(mid * 1.1, caps.brightness.max);
    }

    if (Object.keys(advanced).length > 0) {
      await track.applyConstraints({ advanced: [advanced] } as any);
    }
  } catch (err) {
    console.warn("[CraveCam] enforceCameraSettings:", err);
  }
}

function DirectorCameraInner() {
  const { activePreset = "FLAT_LAY", guidanceNudge, isAligned, ruleResult, setRuleResult } = useContext(CompositionContext) || {};
  const { analyzeFrame } = useCompositionAnalysis();
  const [deviceTilt, setDeviceTilt] = useState<{ pitch: number; roll: number }>({ pitch: 0, roll: 0 });
  const [zoomWarning, setZoomWarning] = useState<boolean>(false);
  const { suggestion: cropSuggestion, updateCropSuggestion, dismissSuggestion } = useAutoCrop();
  const [cropActive, setCropActive] = useState(true); // user can toggle auto-crop

  useEffect(() => {
    const handleOrientation = (e: DeviceOrientationEvent) => {
      const pitch = e.beta !== null ? Math.abs(e.beta) : 0;
      const roll = e.gamma !== null ? e.gamma : 0;
      setDeviceTilt({ pitch, roll });
    };
    if (typeof window !== "undefined") {
      window.addEventListener("deviceorientation", handleOrientation);
      return () => window.removeEventListener("deviceorientation", handleOrientation);
    }
  }, []);
  
  const lastObjectsRef = useRef<any[]>([]); 
  const videoRef = useRef<HTMLVideoElement>(null); 
  const analysisCanvasRef = useRef<HTMLCanvasElement | null>(null); 
  const streamRef = useRef<MediaStream | null>(null); 
  const objectRef = useRef<ObjectDetector | null>(null); 
  const frameRef = useRef(0); 
  const smoothRef = useRef<DetectedSubject | null>(null); 
  const sceneRef = useRef<SceneAssessment>(emptyScene); 
  const lastVideoTimeRef = useRef(-1); 
  const lastAnalysisRef = useRef(0); 
  const lastSceneRef = useRef(0); 
  const fpsFramesRef = useRef<number[]>([]);
  const stabilizerRef = useRef<StabilizerState>({ visible: emptyGuidance, candidate: emptyGuidance, candidateSince: 0 });
  const focusIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  
  const touchStartRef = useRef<number | null>(null);
  
  const [facing, setFacing] = useState<"user" | "environment">("environment"); 
  const [status, setStatus] = useState<"idle" | "loading" | "live" | "denied" | "error">("idle"); 
  const [error, setError] = useState(""); 
  const [subject, setSubject] = useState<DetectedSubject | null>(null); 
  const [scene, setScene] = useState<SceneAssessment>(emptyScene); 
  const [mode, setMode] = useState<FramingMode>("AUTO"); 
  const [activeMode, setActiveMode] = useState<Exclude<FramingMode,"AUTO">>("CLOSE_UP"); 
  const [rawGuidance, setRawGuidance] = useState(emptyGuidance); 
  const [guidance, setGuidance] = useState(emptyGuidance); 
  const [debug, setDebug] = useState(false); 
  const [fps, setFps] = useState(0); 
  const [photo, setPhoto] = useState<string | null>(null);
  // Track whether the camera was ever opened so retake skips the splash
  const [hasOpened, setHasOpened] = useState(false);

  const stopCamera = useCallback(() => {
    cancelAnimationFrame(frameRef.current);
    if (focusIntervalRef.current) { clearInterval(focusIntervalRef.current); focusIntervalRef.current = null; }
    streamRef.current?.getTracks().forEach(track => track.stop());
    streamRef.current = null;
  }, []);
  
  const analyze = useCallback(() => {
    const video = videoRef.current, now = performance.now();
    if (status !== "live" || !video) return;
    if (video.currentTime !== lastVideoTimeRef.current && now - lastAnalysisRef.current >= config.analysisIntervalMs) {
      lastVideoTimeRef.current = video.currentTime; lastAnalysisRef.current = now;
      
      const sceneObs: SceneObservation = { face: null, body: null, faceCount: 0 };
      const sceneInterval = 280;
      
      if(now - lastSceneRef.current > sceneInterval && objectRef.current){
        lastSceneRef.current=now;
        try{
          const objects = objectRef.current.detectForVideo(video, now).detections;
          if (objects && objects.length > 0) {
            lastObjectsRef.current = objects;
            // update smoothRef based on food objects
            const targetObj = objects[0];
            const b = targetObj.boundingBox;
            if (b) {
               smoothRef.current = smoothSubject(smoothRef.current, {
                 centerX: (b.originX! + b.width! / 2) / video.videoWidth,
                 centerY: (b.originY! + b.height! / 2) / video.videoHeight,
                 width: b.width! / video.videoWidth,
                 height: b.height! / video.videoHeight
               });
            }
          } else {
             smoothRef.current = null;
          }
          analysisCanvasRef.current ??= document.createElement("canvas");
          sceneRef.current = assessScene(video, analysisCanvasRef.current, objects, smoothRef.current, facing === "user");
          setScene(sceneRef.current);
        } catch{}
      }

      analyzeFrame({
        objects: lastObjectsRef.current,
        videoWidth: video.videoWidth,
        videoHeight: video.videoHeight,
        mirrored: facing === "user",
        zoomRatio: 1,
        videoElement: video,
      });

      const composition = getCompositionGuidance(sceneObs, mode); 
      const raw = composition.type === "READY" ? sceneGuidance(sceneRef.current) ?? composition : composition; 
      stabilizerRef.current = stabilizeGuidance(stabilizerRef.current, raw, now);
      
      setSubject(smoothRef.current); 
      setActiveMode(mode === "AUTO" ? inferFraming(sceneObs) : mode); 
      setRawGuidance(raw); 
      setGuidance(stabilizerRef.current.visible); 
      fpsFramesRef.current = [...fpsFramesRef.current.filter(t => now - t < 1000), now]; 
      setFps(fpsFramesRef.current.length);

      let subjectBox: BoundingBox | null = null;
      if (smoothRef.current) {
        subjectBox = {
          x: smoothRef.current.centerX - smoothRef.current.width / 2,
          y: smoothRef.current.centerY - smoothRef.current.height / 2,
          width: smoothRef.current.width,
          height: smoothRef.current.height,
        };
      }

      const evalRes = evaluateComposition(
        activePreset || "FLAT_LAY",
        subjectBox,
        sceneRef.current?.edgeDensity || 0.2,
        deviceTilt
      );
      setRuleResult?.(evalRes);

      // Feed detected objects into the auto-crop engine
      if (cropActive && lastObjectsRef.current.length > 0) {
        updateCropSuggestion(
          lastObjectsRef.current,
          video.videoWidth,
          video.videoHeight,
          activePreset || "FLAT_LAY"
        );
      }
    }
    frameRef.current = requestAnimationFrame(analyze);
  }, [activePreset, analyzeFrame, deviceTilt, facing, mode, setRuleResult, status]);
  
  const startCamera = useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) { setError("This browser cannot access a camera."); setStatus("error"); return; }
    stopCamera(); setStatus("loading"); setError(""); setSubject(null); smoothRef.current = null;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ 
        video: { 
          facingMode: { ideal: facing }, 
          width: { ideal: 1920 }, 
          height: { ideal: 1080 },
          // Request continuous focus and exposure upfront
          focusMode: { ideal: "continuous" },
          exposureMode: { ideal: "continuous" },
          whiteBalanceMode: { ideal: "continuous" },
        } as any, 
        audio: false 
      }); 
      streamRef.current = stream;
      const video = videoRef.current; if (!video) return; video.srcObject = stream; await video.play();
      
      const track = stream.getVideoTracks()[0];

      // Apply best-effort focus/exposure/WB constraints immediately
      await enforceCameraSettings(track);

      // Re-enforce every 4 seconds to fight webcam firmware drift
      focusIntervalRef.current = setInterval(() => {
        const t = streamRef.current?.getVideoTracks()[0];
        if (t && t.readyState === "live") enforceCameraSettings(t);
      }, 4000);

      const vision = await FilesetResolver.forVisionTasks("/mediapipe/wasm");
      setStatus("live");
      setHasOpened(true);
      if(!objectRef.current){
        // Track food, tableware, and dining-adjacent objects for better scene understanding
        objectRef.current = await ObjectDetector.createFromOptions(vision,{baseOptions:{modelAssetPath:"/mediapipe/efficientdet_lite0_uint8.tflite"},runningMode:"VIDEO",scoreThreshold:.3,maxResults:10, categoryAllowlist: [
          // Food items
          "pizza", "hot dog", "cake", "sandwich", "donut", "banana", "apple", "orange", "broccoli", "carrot",
          // Tableware & containers
          "bowl", "cup", "wine glass", "bottle", "fork", "knife", "spoon",
          // Surfaces
          "dining table",
        ]});
      }
    } catch (e: any) { 
      const denied = e instanceof DOMException && (e.name === "NotAllowedError" || e.name === "SecurityError"); 
      setStatus(denied ? "denied" : "error"); 
      setError(denied ? "Camera access is needed to direct your photo." : `Error: ${e.message || "Could not start camera."}`); 
    }
  }, [facing, stopCamera]);

  useEffect(() => { if (status === "live") frameRef.current = requestAnimationFrame(analyze); return () => cancelAnimationFrame(frameRef.current); }, [status, analyze]);
  useEffect(() => () => { stopCamera(); objectRef.current?.close(); }, [stopCamera]);
  
  // ── Capture: apply suggested crop if available ──
  const capture = () => {
    const video = videoRef.current;
    if (!video || status !== "live") return;

    const fullCanvas = document.createElement("canvas");
    fullCanvas.width = video.videoWidth;
    fullCanvas.height = video.videoHeight;
    const fullCtx = fullCanvas.getContext("2d");
    if (!fullCtx) return;
    if (facing === "user") { fullCtx.translate(fullCanvas.width, 0); fullCtx.scale(-1, 1); }
    fullCtx.drawImage(video, 0, 0);

    // If there's a significant auto-crop suggestion, apply it
    const crop = cropActive && cropSuggestion?.isSignificant ? cropSuggestion.crop : null;
    if (crop) {
      const sx = Math.round(crop.x * video.videoWidth);
      const sy = Math.round(crop.y * video.videoHeight);
      const sw = Math.round(crop.w * video.videoWidth);
      const sh = Math.round(crop.h * video.videoHeight);

      const croppedCanvas = document.createElement("canvas");
      croppedCanvas.width = sw;
      croppedCanvas.height = sh;
      const croppedCtx = croppedCanvas.getContext("2d");
      if (croppedCtx) {
        croppedCtx.drawImage(fullCanvas, sx, sy, sw, sh, 0, 0, sw, sh);
        setPhoto(croppedCanvas.toDataURL("image/jpeg", 0.92));
      } else {
        setPhoto(fullCanvas.toDataURL("image/jpeg", 0.92));
      }
    } else {
      setPhoto(fullCanvas.toDataURL("image/jpeg", 0.92));
    }
    stopCamera();
  };

  // ── Retake handler: go straight back to the viewfinder, not the splash ──
  const handleRetake = useCallback(() => {
    setPhoto(null);
    // Restart camera immediately → straight to viewfinder
    startCamera().catch(e => console.error(e));
  }, [startCamera]);

  const handleTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length === 2) {
      setZoomWarning(true);
      setTimeout(() => setZoomWarning(false), 3000);
    }
  };

  const handleTapFocus = async (e: React.MouseEvent) => {
    const track = streamRef.current?.getVideoTracks()[0];
    if (!track) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    
    // Show a tap feedback ring
    setTapPoint({ x: e.clientX - rect.left, y: e.clientY - rect.top });
    setTimeout(() => setTapPoint(null), 800);
    
    try {
      const constraints: any = { advanced: [{}] };
      const capabilities: any = track.getCapabilities?.() || {};
      
      if (capabilities.focusMode) {
        constraints.advanced[0].focusMode = "manual";
      }
      if (capabilities.exposureMode) {
        constraints.advanced[0].exposureMode = "manual";
      }
      if (capabilities.pointsOfInterest) {
        constraints.advanced[0].pointsOfInterest = [{ x, y }];
      }
      
      if (Object.keys(constraints.advanced[0]).length > 0) {
        await track.applyConstraints(constraints);
        // After a short pause, go back to continuous
        setTimeout(() => enforceCameraSettings(track), 2000);
      }
    } catch (err) {
      console.warn("Could not apply focus/exposure constraints", err);
    }
  };

  // Tap-to-focus visual feedback
  const [tapPoint, setTapPoint] = useState<{x: number; y: number} | null>(null);

  if (photo) return <PhotoResultScreen image={photo} onRetake={handleRetake} />;
  
  const displayedGuidance: CompositionGuidance = guidanceNudge
    ? {
        type: isAligned ? "READY" : "NO_SUBJECT",
        message: guidanceNudge.text,
        severity: isAligned ? 0 : 0.5,
      }
    : guidance;

  // ── Determine if we should show the splash or not ──
  const showSplash = !hasOpened && (status === "idle" || status === "loading" || status === "denied" || status === "error");
  const showErrorOnly = hasOpened && (status === "denied" || status === "error");

  return <main className="camera-shell" onTouchStart={handleTouchStart} onClick={handleTapFocus}><video ref={videoRef} className={`camera-feed ${facing === "user" ? "mirrored" : ""}`} playsInline muted aria-label="Live camera preview" /><div className="camera-scrim" aria-hidden />
    {status === "live" && <>
      {/* ── Minimal header ── */}
      <header className="camera-header">
        <span className="app-title">CraveCam</span>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <button type="button" onClick={() => setDebug(!debug)} className={debug ? "active" : ""} aria-label="Toggle developer details">
            <Bug size={18} />
          </button>
        </div>
      </header>

      {/* ── Composition overlay ── */}
      <SubjectTargetZone targetZone={ruleResult?.targetZone} isAligned={ruleResult?.isAligned} guidelineType={ruleResult?.activeGuideline} />
      
      {/* ── Tap-to-focus ring animation ── */}
      {tapPoint && (
        <div className="tap-focus-ring" style={{
          left: tapPoint.x,
          top: tapPoint.y,
        }} />
      )}

      {/* ── Zoom warning ── */}
      {zoomWarning && (
        <div className="toast-warning">
          Move physically closer instead of zooming
        </div>
      )}

      <SceneCues />

      {/* ── Debug overlays ── */}
      {debug && subject && <div className="face-box" style={{ left: `${(subject.centerX - subject.width / 2) * 100}%`, top: `${(subject.centerY - subject.height / 2) * 100}%`, width: `${subject.width * 100}%`, height: `${subject.height * 100}%` }}><i /></div>}
      {debug && <DebugOverlay subject={subject} raw={rawGuidance} stable={guidance} fps={fps} />}

      {/* Detection bounding boxes only shown in debug mode */}
      {debug && lastObjectsRef.current.map((obj, i) => {
        const b = obj.boundingBox;
        if (!b || !videoRef.current) return null;
        const vw = videoRef.current.videoWidth || 1;
        const vh = videoRef.current.videoHeight || 1;
        const bx = (b.originX ?? b.x ?? 0) / vw;
        const by = (b.originY ?? b.y ?? 0) / vh;
        const bw = (b.width ?? 0) / vw;
        const bh = (b.height ?? 0) / vh;
        const labelName = obj.categories?.[0]?.categoryName || obj.label;
        const score = obj.categories?.[0]?.score;
        
        return <div key={i} style={{
          position: 'absolute',
          left: `${bx * 100}%`,
          top: `${by * 100}%`,
          width: `${bw * 100}%`,
          height: `${bh * 100}%`,
          border: '1px dashed rgba(255, 107, 107, 0.5)',
          borderRadius: '4px',
          pointerEvents: 'none',
          zIndex: 10,
        }}><span style={{ position: 'absolute', top: -18, left: 0, fontSize: 10, color: '#FF6B6B', background: 'rgba(0,0,0,0.6)', padding: '1px 4px', borderRadius: 3 }}>{labelName} {score ? `${(score * 100).toFixed(0)}%` : ''}</span></div>
      })}

      {/* ── Auto-crop suggestion overlay ── */}
      {cropActive && cropSuggestion?.isSignificant && (
        <>
          {/* Vignette: darken everything outside the suggested crop */}
          <div className="autocrop-vignette" style={{
            position: 'absolute', inset: 0, zIndex: 11, pointerEvents: 'none',
            background: `linear-gradient(to right,
              rgba(0,0,0,0.55) ${cropSuggestion.crop.x * 100}%,
              transparent ${cropSuggestion.crop.x * 100}%,
              transparent ${(cropSuggestion.crop.x + cropSuggestion.crop.w) * 100}%,
              rgba(0,0,0,0.55) ${(cropSuggestion.crop.x + cropSuggestion.crop.w) * 100}%)`,
          }}>
            {/* Top bar */}
            <div style={{
              position: 'absolute',
              left: `${cropSuggestion.crop.x * 100}%`,
              top: 0,
              width: `${cropSuggestion.crop.w * 100}%`,
              height: `${cropSuggestion.crop.y * 100}%`,
              background: 'rgba(0,0,0,0.55)',
            }} />
            {/* Bottom bar */}
            <div style={{
              position: 'absolute',
              left: `${cropSuggestion.crop.x * 100}%`,
              top: `${(cropSuggestion.crop.y + cropSuggestion.crop.h) * 100}%`,
              width: `${cropSuggestion.crop.w * 100}%`,
              height: `${(1 - cropSuggestion.crop.y - cropSuggestion.crop.h) * 100}%`,
              background: 'rgba(0,0,0,0.55)',
            }} />
          </div>

          {/* Crop frame border */}
          <div style={{
            position: 'absolute',
            left: `${cropSuggestion.crop.x * 100}%`,
            top: `${cropSuggestion.crop.y * 100}%`,
            width: `${cropSuggestion.crop.w * 100}%`,
            height: `${cropSuggestion.crop.h * 100}%`,
            border: '2px solid rgba(255, 255, 255, 0.7)',
            borderRadius: '8px',
            pointerEvents: 'none',
            zIndex: 12,
            boxShadow: '0 0 20px rgba(255, 107, 107, 0.3)',
            transition: 'all 0.4s cubic-bezier(0.16, 1, 0.3, 1)',
          }}>
            {/* Corner marks */}
            <div style={{ position: 'absolute', left: -1, top: -1, width: 16, height: 16, borderLeft: '3px solid #FF6B6B', borderTop: '3px solid #FF6B6B', borderRadius: '4px 0 0 0' }} />
            <div style={{ position: 'absolute', right: -1, top: -1, width: 16, height: 16, borderRight: '3px solid #FF6B6B', borderTop: '3px solid #FF6B6B', borderRadius: '0 4px 0 0' }} />
            <div style={{ position: 'absolute', right: -1, bottom: -1, width: 16, height: 16, borderRight: '3px solid #FF6B6B', borderBottom: '3px solid #FF6B6B', borderRadius: '0 0 4px 0' }} />
            <div style={{ position: 'absolute', left: -1, bottom: -1, width: 16, height: 16, borderLeft: '3px solid #FF6B6B', borderBottom: '3px solid #FF6B6B', borderRadius: '0 0 0 4px' }} />
          </div>

          {/* Suggestion toast */}
          <div className="autocrop-toast">
            <Sparkles size={14} />
            <span>{cropSuggestion.reason}</span>
            <button type="button" onClick={(e) => { e.stopPropagation(); dismissSuggestion(); }} style={{
              background: 'none', border: '1px solid rgba(255,255,255,0.3)', color: 'white',
              borderRadius: '999px', padding: '3px 10px', fontSize: '11px', cursor: 'pointer',
              marginLeft: '8px',
            }}>Dismiss</button>
          </div>
        </>
      )}

      {/* ── Guidance pill ── */}
      <div className="guidance-wrap">
        <GuidancePill ruleResult={ruleResult} />
      </div>

      {/* ── Bottom controls ── */}
      <footer className="camera-controls">
        <PresetCarousel />
        <div className="control-row">
          {/* Auto-crop toggle */}
          <button type="button" className={`round-control ${cropActive ? 'active' : ''}`}
            onClick={() => setCropActive(v => !v)} aria-label="Toggle auto-crop"
            style={{
              justifySelf: 'start',
              background: cropActive ? 'rgba(255,107,107,0.25)' : 'rgba(0,0,0,.4)',
              border: cropActive ? '1px solid rgba(255,107,107,0.5)' : 'none',
            }}>
            <Crop size={18} color={cropActive ? '#FF6B6B' : 'white'} />
          </button>
          <button type="button" className={`shutter ${cropSuggestion?.isSignificant && cropActive ? 'has-crop' : ''}`} onClick={capture} aria-label="Take photo"><span /></button>
          <button type="button" className="round-control" onClick={() => setFacing(v => v === "user" ? "environment" : "user")} aria-label="Switch camera"><RefreshCw size={20} /></button>
        </div>
      </footer>
    </>}

    {/* ── Splash screen (only shown on first open) ── */}
    {showSplash && (
      <section className="camera-entry" style={{ zIndex: 99999 }}>
        <div className="entry-mark"><CameraIcon size={32} /></div>
        <p className="app-subtitle">CraveCam</p>
        <h1>{status === "denied" ? "Camera access is off" : status === "error" ? "Camera unavailable" : "Capture the crave."}</h1>
        <span>{error || "Smart, real-time guidance to make your food photos look incredibly appetizing."}</span>
        <button type="button" onClick={() => { startCamera().catch(e => alert(e)); }} disabled={status === "loading"} className="start-btn">
          {status === "loading" ? "Starting camera…" : status === "denied" ? "Try camera again" : "Open Camera"}
        </button>
        {status === "denied" && <small>Allow camera access in your browser settings, then try again.</small>}
      </section>
    )}

    {/* ── Error overlay on subsequent attempts (not the splash) ── */}
    {showErrorOnly && (
      <div className="toast-warning" style={{ top: '50%', transform: 'translate(-50%, -50%)' }}>
        {error || "Camera unavailable"}<br />
        <button type="button" onClick={() => startCamera().catch(() => {})} className="start-btn" style={{ marginTop: '1rem', padding: '.75rem 2rem', fontSize: '.9rem' }}>
          Retry
        </button>
      </div>
    )}

    {status === "live" && <button className="close-camera" type="button" onClick={() => { stopCamera(); setStatus("idle"); setHasOpened(false); }} aria-label="Close camera"><X size={20} /></button>}
  </main>;
}

export default function DirectorCameraScreen() {
  const context = useContext(CompositionContext);
  if (!context) {
    return (
      <CompositionProvider>
        <DirectorCameraInner />
      </CompositionProvider>
    );
  }
  return <DirectorCameraInner />;
}
