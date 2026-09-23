export type LightingQuality = 'harsh_shadow' | 'backlit' | 'flat' | 'balanced';

export interface FaceIlluminationAssessment {
  quality: LightingQuality;
  leftCheekLuminance: number;     // 0 to 1 (MediaPipe Face Mesh landmark 234)
  rightCheekLuminance: number;    // 0 to 1 (MediaPipe Face Mesh landmark 454)
  foreheadLuminance: number;      // 0 to 1 (MediaPipe Face Mesh landmark 10)
  chinLuminance: number;          // 0 to 1 (MediaPipe Face Mesh landmark 152)
  overallFaceLuminance: number;   // 0 to 1 (average of 234, 454, 10, 152)
  backgroundLuminance: number;    // ambient background 0 to 1
  contrastRatio: number;          // L_bright / L_dark
  primaryLightDirection: 'left' | 'right' | 'front' | 'back' | 'overhead';
  angleDegrees: number;           // -180 to 180 (0 is straight ahead, -90 left, +90 right, 180 back)
  actionNudgeText: string | null; // e.g. "Turn face slightly toward the light" | "Step out of backlighting"
  turnDirection: 'left' | 'right' | null;
  centerLuminance?: number;       // alias for forehead backwards compatibility
}

export interface FaceSampleRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface LandmarkPoint {
  x: number;
  y: number;
  z?: number;
}

/**
 * Reusable offscreen canvas buffer for high-speed sampling
 */
let sharedCanvas: HTMLCanvasElement | null = null;
let sharedCtx: CanvasRenderingContext2D | null = null;

function getSharedCanvas(size: number = 64) {
  if (typeof document === 'undefined') return null;
  if (!sharedCanvas) {
    sharedCanvas = document.createElement('canvas');
    sharedCanvas.width = size;
    sharedCanvas.height = size;
    sharedCtx = sharedCanvas.getContext('2d', { willReadFrequently: true });
  }
  return { canvas: sharedCanvas, ctx: sharedCtx, size };
}

/**
 * Computes perceived relative luminance from RGB (BT.709 standard)
 */
export function calculateLuminance(r: number, g: number, b: number): number {
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
}

/**
 * Samples average luminance in a circular/box area on a downscaled canvas buffer
 */
function samplePatchLuminance(
  data: Uint8ClampedArray,
  canvasSize: number,
  normX: number,
  normY: number,
  normRadius: number
): number {
  const cx = Math.floor(Math.max(0, Math.min(1, normX)) * canvasSize);
  const cy = Math.floor(Math.max(0, Math.min(1, normY)) * canvasSize);
  const r = Math.max(1, Math.floor(normRadius * canvasSize));

  let totalLum = 0;
  let sampleCount = 0;

  for (let dy = -r; dy <= r; dy++) {
    const py = cy + dy;
    if (py < 0 || py >= canvasSize) continue;
    for (let dx = -r; dx <= r; dx++) {
      const px = cx + dx;
      if (px < 0 || px >= canvasSize) continue;
      if (dx * dx + dy * dy <= r * r) {
        const idx = (py * canvasSize + px) * 4;
        const lum = calculateLuminance(data[idx], data[idx + 1], data[idx + 2]);
        totalLum += lum;
        sampleCount++;
      }
    }
  }

  return sampleCount > 0 ? totalLum / sampleCount : 0.5;
}

/**
 * Evaluates illumination directly from provided luminance readings.
 * Conforms strictly to the mathematical parameters:
 * - Samples Left cheek (234), Right cheek (454), Forehead (10), Chin (152)
 * - If ratio L_bright / L_dark > 3.5: "Turn face slightly toward the light"
 * - If overall face L < 0.25 while background L > 0.70: "Step out of backlighting"
 */
export function evaluateLightingMetrics(
  leftCheek: number,
  rightCheek: number,
  forehead: number,
  chinOrBackground: number,
  maybeBackground?: number,
  mirrored: boolean = false
): FaceIlluminationAssessment {
  let chin: number;
  let background: number;

  if (maybeBackground !== undefined) {
    chin = chinOrBackground;
    background = maybeBackground;
  } else {
    // 4-argument backwards compatibility
    chin = (leftCheek + rightCheek) / 2;
    background = chinOrBackground;
  }

  // Calculate bright and dark cheeks
  const brightCheek = Math.max(leftCheek, rightCheek);
  const darkCheek = Math.max(0.001, Math.min(leftCheek, rightCheek));
  const contrastRatio = brightCheek / darkCheek;

  // Mathematical Variable: Overall Face L from Left Cheek, Right Cheek, Forehead, Chin
  const overallFaceLum = (leftCheek + rightCheek + forehead + chin) / 4;

  // 1. Harsh Contrast Check
  // If ratio L_bright / L_dark > 3.5 (harsh contrast): "Turn face slightly toward the light"
  if (contrastRatio > 3.5) {
    const lightOnLeft = leftCheek > rightCheek;
    const primaryLightDirection = lightOnLeft ? 'left' : 'right';
    const angleDegrees = lightOnLeft ? -65 : 65;
    const turnDirection = lightOnLeft ? 'left' : 'right';

    return {
      quality: 'harsh_shadow',
      leftCheekLuminance: leftCheek,
      rightCheekLuminance: rightCheek,
      foreheadLuminance: forehead,
      chinLuminance: chin,
      centerLuminance: forehead,
      overallFaceLuminance: overallFaceLum,
      backgroundLuminance: background,
      contrastRatio,
      primaryLightDirection,
      angleDegrees,
      actionNudgeText: "Turn face slightly toward the light",
      turnDirection,
    };
  }

  // 2. Silhouette Backlighting Check
  // If overall face L < 0.25 while background L > 0.70 (silhouette backlight): "Step out of backlighting"
  if (overallFaceLum < 0.25 && background > 0.70) {
    return {
      quality: 'backlit',
      leftCheekLuminance: leftCheek,
      rightCheekLuminance: rightCheek,
      foreheadLuminance: forehead,
      chinLuminance: chin,
      centerLuminance: forehead,
      overallFaceLuminance: overallFaceLum,
      backgroundLuminance: background,
      contrastRatio,
      primaryLightDirection: 'back',
      angleDegrees: 180,
      actionNudgeText: "Step out of backlighting",
      turnDirection: null,
    };
  }

  // 3. Flat / Washed-out Lighting
  const cheekDiff = Math.abs(leftCheek - rightCheek);
  if (cheekDiff < 0.05 && contrastRatio < 1.25 && overallFaceLum >= 0.30 && overallFaceLum <= 0.70) {
    return {
      quality: 'flat',
      leftCheekLuminance: leftCheek,
      rightCheekLuminance: rightCheek,
      foreheadLuminance: forehead,
      chinLuminance: chin,
      centerLuminance: forehead,
      overallFaceLuminance: overallFaceLum,
      backgroundLuminance: background,
      contrastRatio,
      primaryLightDirection: 'front',
      angleDegrees: 0,
      actionNudgeText: null,
      turnDirection: null,
    };
  }

  // 4. Balanced / Flattering Lighting (ratio <= 3.5)
  let angleDegrees = 0;
  let primaryLightDirection: 'left' | 'right' | 'front' | 'back' | 'overhead' = 'front';
  if (cheekDiff >= 0.05) {
    if (leftCheek > rightCheek) {
      primaryLightDirection = 'left';
      angleDegrees = -35;
    } else {
      primaryLightDirection = 'right';
      angleDegrees = 35;
    }
  }

  return {
    quality: 'balanced',
    leftCheekLuminance: leftCheek,
    rightCheekLuminance: rightCheek,
    foreheadLuminance: forehead,
    chinLuminance: chin,
    centerLuminance: forehead,
    overallFaceLuminance: overallFaceLum,
    backgroundLuminance: background,
    contrastRatio,
    primaryLightDirection,
    angleDegrees,
    actionNudgeText: null,
    turnDirection: null,
  };
}

/**
 * Analyzes live facial illumination by sampling MediaPipe 468 Face Mesh landmarks (234, 454, 10, 152)
 * on a downscaled canvas buffer.
 */
export function assessFaceIllumination(
  video: HTMLVideoElement | HTMLCanvasElement | null,
  faceBox: FaceSampleRect | null,
  mirrored: boolean = false,
  faceMeshLandmarks?: LandmarkPoint[] | null
): FaceIlluminationAssessment | null {
  if (!video || !faceBox) return null;

  const buf = getSharedCanvas(64);
  if (!buf || !buf.ctx) return null;

  const { ctx, size } = buf;

  try {
    // Draw current video frame downscaled to 64x64
    ctx.drawImage(video, 0, 0, size, size);
    const imgData = ctx.getImageData(0, 0, size, size).data;

    let leftCheekLum: number;
    let rightCheekLum: number;
    let foreheadLum: number;
    let chinLum: number;

    // Check if direct 468 MediaPipe Face Mesh landmarks are available
    if (faceMeshLandmarks && faceMeshLandmarks.length > 454) {
      // Left cheek landmark 234
      const p234 = faceMeshLandmarks[234];
      leftCheekLum = samplePatchLuminance(imgData, size, p234.x, p234.y, 0.03);

      // Right cheek landmark 454
      const p454 = faceMeshLandmarks[454];
      rightCheekLum = samplePatchLuminance(imgData, size, p454.x, p454.y, 0.03);

      // Forehead landmark 10
      const p10 = faceMeshLandmarks[10];
      foreheadLum = samplePatchLuminance(imgData, size, p10.x, p10.y, 0.03);

      // Chin landmark 152
      const p152 = faceMeshLandmarks[152];
      chinLum = samplePatchLuminance(imgData, size, p152.x, p152.y, 0.03);
    } else {
      // Sample at canonical coordinates of MediaPipe Face Mesh landmarks within the normalized faceBox:
      // Landmark 234 (Left cheek bone): (x + w * 0.18, y + h * 0.55)
      // Landmark 454 (Right cheek bone): (x + w * 0.82, y + h * 0.55)
      // Landmark 10 (Forehead center): (x + w * 0.50, y + h * 0.15)
      // Landmark 152 (Chin tip): (x + w * 0.50, y + h * 0.92)
      const { x, y, width, height } = faceBox;
      const sampleRadius = Math.max(0.01, width * 0.08);

      leftCheekLum = samplePatchLuminance(
        imgData,
        size,
        x + width * 0.18,
        y + height * 0.55,
        sampleRadius
      );

      rightCheekLum = samplePatchLuminance(
        imgData,
        size,
        x + width * 0.82,
        y + height * 0.55,
        sampleRadius
      );

      foreheadLum = samplePatchLuminance(
        imgData,
        size,
        x + width * 0.50,
        y + height * 0.15,
        sampleRadius
      );

      chinLum = samplePatchLuminance(
        imgData,
        size,
        x + width * 0.50,
        y + height * 0.92,
        sampleRadius
      );
    }

    // Sample Ambient Background (top-left, top-center, top-right outer perimeter)
    const bg1 = samplePatchLuminance(imgData, size, 0.15, 0.10, 0.06);
    const bg2 = samplePatchLuminance(imgData, size, 0.50, 0.06, 0.06);
    const bg3 = samplePatchLuminance(imgData, size, 0.85, 0.10, 0.06);
    const backgroundLum = (bg1 + bg2 + bg3) / 3;

    return evaluateLightingMetrics(
      leftCheekLum,
      rightCheekLum,
      foreheadLum,
      chinLum,
      backgroundLum,
      mirrored
    );
  } catch {
    return null;
  }
}
