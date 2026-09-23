"use client";
import { useCallback, useContext, useEffect, useRef, useState } from "react";
import { FaceDetector, FilesetResolver, ObjectDetector, PoseLandmarker } from "@mediapipe/tasks-vision";
import { Bug, Camera as CameraIcon, RefreshCw, X } from "lucide-react";
import { getCompositionGuidance, inferFraming } from "../composition/compositionEngine";
import { compositionConfig as config } from "../composition/compositionConfig";
import { smoothSubject, stabilizeGuidance, type StabilizerState } from "../composition/guidanceStabilizer";
import { normalizeDetection } from "../vision/normalizeDetection";
import { normalizePose } from "../vision/normalizePose";
import { assessScene, sceneGuidance } from "../vision/sceneAnalysis";
import type { BoundingBox, BodyObservation, CompositionGuidance, DetectedSubject, FramingMode, SceneObservation } from "../types/composition";
import type { SceneAssessment } from "../types/scene";
import GuidancePill from "../components/GuidancePill";
import DebugOverlay from "../overlays/DebugOverlay";
import PhotoResultScreen from "../results/PhotoResultScreen";
import SceneCues from "../overlays/SceneCues";
import GenreCarousel from "../components/GenreCarousel";
import PresetCarousel from "../components/PresetCarousel";
import SubjectTargetZone from "../overlays/SubjectTargetZone";
import LivePoseOutline from "../overlays/LivePoseOutline";
import { evaluateComposition } from "../engines/compositionEngine";
import { CompositionContext, CompositionProvider } from "../context/CompositionContext";
import { useCompositionAnalysis } from "../hooks/useCompositionAnalysis";
const emptyGuidance: CompositionGuidance = { type: "NO_SUBJECT", message: "Point the camera at a person", severity: 1 };
const emptyScene: SceneAssessment = { brightness:.5, highlightClip:0, shadowClip:0, edgeDensity:0, subjectContrast:.25, lightTone:"good", backgroundTone:"good", separationTone:"good", objects:[] };
function DirectorCameraInner() {
  const { activePreset = "PEOPLE", guidanceNudge, isAligned, ruleResult, setRuleResult } = useContext(CompositionContext) || {};
  const { analyzeFrame } = useCompositionAnalysis();
  const [deviceTilt, setDeviceTilt] = useState<{ pitch: number; roll: number }>({ pitch: 0, roll: 0 });

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
  const lastPosesRef = useRef<any[]>([]);
  const lastObjectsRef = useRef<any[]>([]);
  const videoRef = useRef<HTMLVideoElement>(null); const analysisCanvasRef = useRef<HTMLCanvasElement | null>(null); const streamRef = useRef<MediaStream | null>(null); const detectorRef = useRef<FaceDetector | null>(null); const poseRef = useRef<PoseLandmarker | null>(null); const objectRef = useRef<ObjectDetector | null>(null); const frameRef = useRef(0); const smoothRef = useRef<DetectedSubject | null>(null); const bodyRef = useRef<BodyObservation | null>(null); const sceneRef = useRef<SceneAssessment>(emptyScene); const faceCountRef = useRef(0); const lastFaceSeenRef = useRef(0); const lastVideoTimeRef = useRef(-1); const lastAnalysisRef = useRef(0); const lastPoseRef = useRef(0); const lastSceneRef = useRef(0); const fpsFramesRef = useRef<number[]>([]);
  const stabilizerRef = useRef<StabilizerState>({ visible: emptyGuidance, candidate: emptyGuidance, candidateSince: 0 });
  const [facing, setFacing] = useState<"user" | "environment">("environment"); const [status, setStatus] = useState<"idle" | "loading" | "live" | "denied" | "error">("idle"); const [error, setError] = useState(""); const [subject, setSubject] = useState<DetectedSubject | null>(null); const [body, setBody] = useState<BodyObservation | null>(null); const [faceCount, setFaceCount] = useState(0); const [scene, setScene] = useState<SceneAssessment>(emptyScene); const [mode, setMode] = useState<FramingMode>("AUTO"); const [activeMode, setActiveMode] = useState<Exclude<FramingMode,"AUTO">>("CLOSE_UP"); const [rawGuidance, setRawGuidance] = useState(emptyGuidance); const [guidance, setGuidance] = useState(emptyGuidance); const [debug, setDebug] = useState(false); const [fps, setFps] = useState(0); const [photo, setPhoto] = useState<string | null>(null); const [zoom, setZoom] = useState(1); const [zoomRange, setZoomRange] = useState({ min: 1, max: 1 });
  const stopCamera = useCallback(() => { cancelAnimationFrame(frameRef.current); streamRef.current?.getTracks().forEach(track => track.stop()); streamRef.current = null; }, []);
  const analyze = useCallback(() => {
    const video = videoRef.current, detector = detectorRef.current, now = performance.now();
    if (status !== "live" || !video || !detector) return;
    if (video.currentTime !== lastVideoTimeRef.current && now - lastAnalysisRef.current >= config.analysisIntervalMs) {
      lastVideoTimeRef.current = video.currentTime; lastAnalysisRef.current = now;
      let detections: ReturnType<FaceDetector["detectForVideo"]>["detections"] = [];
      try { detections = detector.detectForVideo(video, now).detections; } catch { frameRef.current = requestAnimationFrame(analyze); return; }
      const largest = detections.reduce<(typeof detections)[number] | null>((best, item) => !item.boundingBox ? best : (!best?.boundingBox || item.boundingBox.width * item.boundingBox.height > best.boundingBox.width * best.boundingBox.height ? item : best), null);
      if (largest?.boundingBox) { const found = normalizeDetection(largest.boundingBox, video.videoWidth, video.videoHeight, video.clientWidth, video.clientHeight, facing === "user"); found.confidence = largest.categories?.[0]?.score; smoothRef.current = smoothSubject(smoothRef.current, found); lastFaceSeenRef.current=now; faceCountRef.current=detections.length; } else if(now-lastFaceSeenRef.current>650){smoothRef.current=null;faceCountRef.current=0;}
      if(now-lastPoseRef.current>(activePreset === "PEOPLE" ? 180 : 360)&&poseRef.current){
        lastPoseRef.current=now;
        try{
          const poses=poseRef.current.detectForVideo(video,now).landmarks;
          if (poses && poses.length > 0) {
            lastPosesRef.current = poses;
            if (poses[0]) {
              bodyRef.current=normalizePose(poses[0],video.videoWidth,video.videoHeight,video.clientWidth,video.clientHeight,facing==="user");
            }
          }
        }catch{}
      }
      const nextBody=bodyRef.current;
      const scene: SceneObservation = { face: smoothRef.current, body: nextBody, faceCount: faceCountRef.current };
      const sceneInterval = activePreset === "FOOD" ? 280 : 2400;
      if(now-lastSceneRef.current>sceneInterval&&objectRef.current){
        lastSceneRef.current=now;
        try{
          const objects=objectRef.current.detectForVideo(video,now).detections;
          if (objects && objects.length > 0) {
            lastObjectsRef.current = objects;
          }
          analysisCanvasRef.current??=document.createElement("canvas");
          sceneRef.current=assessScene(video,analysisCanvasRef.current,objects,smoothRef.current,facing==="user");
          setScene(sceneRef.current);
        }catch{}
      }

      // Continuously evaluate scene composition across faces, poses, and objects (throttled to ~300ms)
      analyzeFrame({
        faces: detections,
        poses: lastPosesRef.current,
        objects: lastObjectsRef.current,
        videoWidth: video.videoWidth,
        videoHeight: video.videoHeight,
        mirrored: facing === "user",
        zoomRatio: zoom,
        videoElement: video,
      });

      const composition = getCompositionGuidance(scene, mode); const raw = composition.type === "READY" ? sceneGuidance(sceneRef.current) ?? composition : composition; stabilizerRef.current = stabilizeGuidance(stabilizerRef.current, raw, now);
      setSubject(smoothRef.current); setBody(nextBody); setFaceCount(faceCountRef.current); setActiveMode(mode === "AUTO" ? inferFraming(scene) : mode); setRawGuidance(raw); setGuidance(stabilizerRef.current.visible); fpsFramesRef.current = [...fpsFramesRef.current.filter(t => now - t < 1000), now]; setFps(fpsFramesRef.current.length);

      // Evaluate multi-genre pure geometric composition rules
      let subjectBox: BoundingBox | null = null;
      if (smoothRef.current) {
        const portraitScale = activePreset === "PEOPLE" ? 2.2 : 1.0;
        subjectBox = {
          x: smoothRef.current.centerX - smoothRef.current.width / 2,
          y: smoothRef.current.centerY - smoothRef.current.height / 2,
          width: smoothRef.current.width,
          height: Math.min(0.92, smoothRef.current.height * portraitScale),
        };
        if (nextBody && activePreset === "PEOPLE") {
          subjectBox.height = nextBody.height;
        }
      } else if (nextBody) {
        subjectBox = {
          x: nextBody.left,
          y: nextBody.top,
          width: nextBody.width,
          height: nextBody.height,
        };
      }

      const evalRes = evaluateComposition(
        activePreset || "PEOPLE",
        subjectBox,
        sceneRef.current?.edgeDensity || 0.2,
        deviceTilt
      );
      setRuleResult?.(evalRes);
    }
    frameRef.current = requestAnimationFrame(analyze);
  }, [activePreset, analyzeFrame, deviceTilt, facing, mode, setRuleResult, status]);
  const startCamera = useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) { setError("This browser cannot access a camera."); setStatus("error"); return; }
    stopCamera(); setStatus("loading"); setError(""); setSubject(null); smoothRef.current = null;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: facing }, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false }); streamRef.current = stream;
      const video = videoRef.current; if (!video) return; video.srcObject = stream; await video.play();
      const caps = stream.getVideoTracks()[0].getCapabilities?.() as MediaTrackCapabilities & { zoom?: { min: number; max: number } }; setZoomRange(caps?.zoom ? { min: caps.zoom.min, max: caps.zoom.max } : { min: 1, max: 1 }); setZoom(1);
      const vision = await FilesetResolver.forVisionTasks("/mediapipe/wasm");
      if (!detectorRef.current) detectorRef.current = await FaceDetector.createFromOptions(vision, { baseOptions: { modelAssetPath: "/mediapipe/blaze_face_short_range.tflite", delegate: "GPU" }, runningMode: "VIDEO", minDetectionConfidence: 0.35, minSuppressionThreshold:.25 });
      setStatus("live");
      if(!poseRef.current||!objectRef.current){void Promise.allSettled([!poseRef.current?PoseLandmarker.createFromOptions(vision,{baseOptions:{modelAssetPath:"/mediapipe/pose_landmarker_lite.task"},runningMode:"VIDEO",numPoses:1,minPoseDetectionConfidence:.45,minTrackingConfidence:.45}):Promise.resolve(null),!objectRef.current?ObjectDetector.createFromOptions(vision,{baseOptions:{modelAssetPath:"/mediapipe/efficientdet_lite0_uint8.tflite"},runningMode:"VIDEO",scoreThreshold:.45,maxResults:6}):Promise.resolve(null)]).then(results=>{const poseResult=results[0],objectResult=results[1];if(poseResult.status==="fulfilled"&&poseResult.value)poseRef.current=poseResult.value;if(objectResult.status==="fulfilled"&&objectResult.value)objectRef.current=objectResult.value;});}
    } catch (e) { const denied = e instanceof DOMException && (e.name === "NotAllowedError" || e.name === "SecurityError"); setStatus(denied ? "denied" : "error"); setError(denied ? "Camera access is needed to direct your photo." : "The camera or face detector could not start."); }
  }, [facing, stopCamera]);
  useEffect(() => { if (status === "live") frameRef.current = requestAnimationFrame(analyze); return () => cancelAnimationFrame(frameRef.current); }, [status, analyze]);
  useEffect(() => { if (status === "live") void startCamera(); }, [facing]);
  useEffect(() => () => { stopCamera(); detectorRef.current?.close(); poseRef.current?.close(); objectRef.current?.close(); }, [stopCamera]);
  const capture = () => { const video = videoRef.current; if (!video || status !== "live") return; const canvas = document.createElement("canvas"); canvas.width = video.videoWidth; canvas.height = video.videoHeight; const ctx = canvas.getContext("2d"); if (!ctx) return; if (facing === "user") { ctx.translate(canvas.width, 0); ctx.scale(-1, 1); } ctx.drawImage(video, 0, 0); setPhoto(canvas.toDataURL("image/jpeg", .92)); stopCamera(); };
  const setHardwareZoom = async (value: number) => { const track = streamRef.current?.getVideoTracks()[0]; if (!track) return; try { await track.applyConstraints({ advanced: [{ zoom: value } as MediaTrackConstraintSet] }); setZoom(value); } catch {} };
  if (photo) return <PhotoResultScreen image={photo} onRetake={() => { setPhoto(null); setStatus("idle"); }} />;
  const displayedGuidance: CompositionGuidance = (activePreset === "FOOD" || activePreset === "PEOPLE") && guidanceNudge
    ? {
        type: isAligned ? "READY" : "NO_SUBJECT",
        message: guidanceNudge.text,
        severity: isAligned ? 0 : 0.5,
        reason: isAligned
          ? (activePreset === "FOOD" ? "Triangular balance locked" : "Eye line & posture locked")
          : (activePreset === "FOOD" ? "Align dishes to triangle nodes" : "Tilt slightly down to eye line"),
      }
    : guidance;
  return <main className="camera-shell"><video ref={videoRef} className={`camera-feed ${facing === "user" ? "mirrored" : ""}`} playsInline muted aria-label="Live camera preview" /><div className="camera-scrim" aria-hidden />
    {status === "live" && <>
      <header className="camera-header">
        <span>PHOTO DIRECTOR</span>
        <strong>
          {activePreset === "PEOPLE"
            ? "People & Poses"
            : activePreset === "FOOD"
            ? (ruleResult?.activeGuideline === "OVERHEAD_CROSSHAIR" ? "Overhead Flatlay" : "Food & Tableware")
            : activePreset === "NATURE"
            ? "Nature & Scenic"
            : activePreset === "STREET"
            ? "Street & Architecture"
            : "Macro & Detail"}
        </strong>
        <button type="button" onClick={() => setDebug(!debug)} className={debug ? "active" : ""} aria-label="Toggle developer details">
          <Bug size={20} />
        </button>
      </header>

      {/* Clean genre photographic guidelines (subtle thirds, horizon, overhead reticle) */}
      <SubjectTargetZone targetZone={ruleResult?.targetZone} isAligned={ruleResult?.isAligned} guidelineType={ruleResult?.activeGuideline} />

      {/* Live Pose Outline: Only outlines the real person when composition is locked */}
      <LivePoseOutline isAligned={ruleResult?.isAligned ?? false} body={body} subject={subject} />

      {/* Clean perimeter warnings if frame edges are clipped */}
      <SceneCues />

      {/* Developer debug overlays (only shown when bug icon clicked) */}
      {debug && body && <div className="body-box" style={{ left: `${body.left * 100}%`, top: `${body.top * 100}%`, width: `${body.width * 100}%`, height: `${body.height * 100}%` }} />}
      {debug && subject && <div className="face-box" style={{ left: `${(subject.centerX - subject.width / 2) * 100}%`, top: `${(subject.centerY - subject.height / 2) * 100}%`, width: `${subject.width * 100}%`, height: `${subject.height * 100}%` }}><i /></div>}
      {debug && <DebugOverlay subject={subject} raw={rawGuidance} stable={guidance} fps={fps} />}

      {/* Single authoritative Guidance Pill docked cleanly above preset carousel */}
      <div className="guidance-wrap">
        <GuidancePill ruleResult={ruleResult} />
      </div>

      {/* Clean bottom controls with multi-genre selector */}
      <footer className="camera-controls">
        <PresetCarousel />
        <div className="zoom-options" aria-label="Zoom">
          {zoomRange.max > 1.2 ? [1, Math.min(2, zoomRange.max)].filter((v, i, a) => v >= zoomRange.min && a.indexOf(v) === i).map(v => <button key={v} className={Math.abs(zoom - v) < .05 ? "active" : ""} onClick={() => setHardwareZoom(v)}>{v}×</button>) : <span>1×</span>}
        </div>
        <div className="control-row">
          <span className="control-spacer" />
          <button type="button" className="shutter" onClick={capture} aria-label="Take photo"><span /></button>
          <button type="button" className="round-control" onClick={() => setFacing(v => v === "user" ? "environment" : "user")} aria-label="Switch camera"><RefreshCw size={22} /></button>
        </div>
      </footer>
    </>}
    {(status === "idle" || status === "loading" || status === "denied" || status === "error") && <section className="camera-entry"><div className="entry-mark"><CameraIcon size={28} /></div><p>AI PHOTO DIRECTOR</p><h1>{status === "denied" ? "Camera access is off" : status === "error" ? "Camera unavailable" : "Frame it better, as you shoot."}</h1><span>{error || "Live, private guidance helps you position the camera before you take the photo."}</span><button type="button" onClick={startCamera} disabled={status === "loading"}>{status === "loading" ? "Starting camera…" : status === "denied" ? "Try camera again" : "Open camera"}</button>{status === "denied" && <small>Allow camera access in your browser settings, then try again.</small>}</section>}
    {status === "live" && <button className="close-camera" type="button" onClick={() => { stopCamera(); setStatus("idle"); }} aria-label="Close camera"><X size={20} /></button>}
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
