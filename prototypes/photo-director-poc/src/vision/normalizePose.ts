import type { BodyObservation } from "../types/composition";
type Landmark = { x: number; y: number; visibility?: number };
const visible = (points: Landmark[], ids: number[]) => ids.every(i => (points[i]?.visibility ?? 1) > .45 && points[i].x >= 0 && points[i].x <= 1 && points[i].y >= 0 && points[i].y <= 1);
export function normalizePose(points: Landmark[], videoWidth: number, videoHeight: number, previewWidth: number, previewHeight: number, mirrored: boolean): BodyObservation | null {
  const reliable = points.filter(p => (p.visibility ?? 1) > .35);
  if (reliable.length < 6) return null;
  const scale = Math.max(previewWidth / videoWidth, previewHeight / videoHeight);
  const cropX = (videoWidth * scale - previewWidth) / 2, cropY = (videoHeight * scale - previewHeight) / 2;
  const viewX = (x: number) => { const n = (x * videoWidth * scale - cropX) / previewWidth; return mirrored ? 1 - n : n; };
  const viewY = (y: number) => (y * videoHeight * scale - cropY) / previewHeight;
  const xs = reliable.map(p => viewX(p.x)), ys = reliable.map(p => viewY(p.y));
  const left = Math.min(...xs), right = Math.max(...xs), top = Math.min(...ys), bottom = Math.max(...ys);
  return { left, right, top, bottom, centerX: (left + right) / 2, centerY: (top + bottom) / 2, width: right - left, height: bottom - top, shouldersVisible: visible(points, [11,12]), hipsVisible: visible(points, [23,24]), kneesVisible: visible(points, [25,26]), anklesVisible: visible(points, [27,28]) };
}
