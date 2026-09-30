import type { CompositionGuidance, DetectedSubject } from "../types/composition";
export default function DebugOverlay({ subject, raw, stable, fps }: { subject: DetectedSubject | null; raw: CompositionGuidance; stable: CompositionGuidance; fps: number }) {
  return <aside className="debug-overlay"><strong>FACE TRACKER</strong><span>x {subject?.centerX.toFixed(2) ?? "—"} · y {subject?.centerY.toFixed(2) ?? "—"}</span><span>w {subject?.width.toFixed(2) ?? "—"} · h {subject?.height.toFixed(2) ?? "—"}</span><span>raw {raw.type}</span><span>shown {stable.type}</span><span>{fps} fps analysis</span></aside>;
}
