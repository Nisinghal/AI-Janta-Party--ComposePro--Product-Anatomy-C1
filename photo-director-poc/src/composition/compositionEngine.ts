import { compositionConfig as c } from "./compositionConfig";
import type { CompositionGuidance, FramingMode, SceneObservation } from "../types/composition";
const out = (type: CompositionGuidance["type"], message: string, severity: number, reason?: string): CompositionGuidance => ({ type, message, reason, severity: Math.min(1, Math.max(0, severity)) });
export function inferFraming(scene: SceneObservation): Exclude<FramingMode, "AUTO"> {
  if (scene.body?.anklesVisible && scene.body.height > .62) return "FULL_BODY";
  if (scene.body?.hipsVisible || scene.body?.kneesVisible) return "WAIST_UP";
  return "CLOSE_UP";
}
export function getCompositionGuidance(scene: SceneObservation, requestedMode: FramingMode): CompositionGuidance {
  const s = scene.face, body = scene.body, mode = requestedMode === "AUTO" ? inferFraming(scene) : requestedMode;
  if (!s && !body) return out("NO_SUBJECT", "Point the camera at a person", 1, "No person detected");
  if (scene.faceCount > 1) return out("READY", "Group framing found", 0, "Keeping everyone visible");
  if (mode === "FULL_BODY") {
    if (!body?.anklesVisible) return out("MOVE_BACK", "Step back to include their feet", .9, "Full-body framing");
    if (body.height < .68) return out("MOVE_CLOSER", "Move closer", .55, "Make the person more prominent");
    if (body.height > .9 || body.top < .035 || body.bottom > .96) return out("MOVE_BACK", "Step back slightly", .7, "Leave space around the whole body");
    if (body.centerX < .43) return out("MOVE_LEFT", "Move slightly left", .5, "Centre the full silhouette");
    if (body.centerX > .57) return out("MOVE_RIGHT", "Move slightly right", .5, "Centre the full silhouette");
    if (body.top < .06) return out("MOVE_UP", "Raise the phone", .45, "Add comfortable headroom");
    if (body.bottom > .93) return out("MOVE_DOWN", "Lower the phone", .45, "Keep space below the feet");
    return out("READY", "Full-body frame looks good", 0, "Balanced headroom and footroom");
  }
  if (!s) return out("NO_SUBJECT", "Bring their face into view", 1, "A face anchors this portrait");
  const minFace = mode === "CLOSE_UP" ? .24 : .14, maxFace = mode === "CLOSE_UP" ? .42 : .27;
  if (s.height < minFace) return out("MOVE_CLOSER", mode === "CLOSE_UP" ? "Move closer for a stronger portrait" : "Move closer", .7, "The subject is too small in the frame");
  if (s.height > maxFace) return out("MOVE_BACK", "Step back slightly", .7, "Leave breathing room around the subject");
  if (mode === "WAIST_UP" && body && !body.shouldersVisible) return out("MOVE_BACK", "Step back to include both shoulders", .7, "Avoid awkward shoulder crops");
  if (s.centerX < c.acceptableXMin) return out("MOVE_LEFT", "Move slightly left", .55, "Place the face near a vertical third");
  if (s.centerX > c.acceptableXMax) return out("MOVE_RIGHT", "Move slightly right", .55, "Place the face near a vertical third");
  if (s.centerY < c.acceptableYMin) return out("MOVE_UP", "Raise the phone", .5, "Give the portrait better headroom");
  if (s.centerY > c.acceptableYMax) return out("MOVE_DOWN", "Lower the phone", .5, "Bring the eyes toward the upper third");
  return out("READY", mode === "CLOSE_UP" ? "Portrait framing looks good" : "Waist-up framing looks good", 0, "Subject scale, placement and headroom are balanced");
}
