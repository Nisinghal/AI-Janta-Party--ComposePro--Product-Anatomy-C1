import type { DetectedSubject } from "../types/composition";
interface Box { originX: number; originY: number; width: number; height: number }
export function normalizeDetection(box: Box, videoWidth: number, videoHeight: number, previewWidth: number, previewHeight: number, mirrored: boolean): DetectedSubject {
  const scale = Math.max(previewWidth / videoWidth, previewHeight / videoHeight);
  const renderedWidth = videoWidth * scale, renderedHeight = videoHeight * scale;
  const cropX = (renderedWidth - previewWidth) / 2, cropY = (renderedHeight - previewHeight) / 2;
  const sourceCenterX = box.originX + box.width / 2, sourceCenterY = box.originY + box.height / 2;
  const visibleX = (sourceCenterX * scale - cropX) / previewWidth;
  return { centerX: mirrored ? 1 - visibleX : visibleX, centerY: (sourceCenterY * scale - cropY) / previewHeight, width: box.width * scale / previewWidth, height: box.height * scale / previewHeight };
}
