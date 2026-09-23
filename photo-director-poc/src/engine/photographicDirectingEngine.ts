import type { FaceIlluminationAssessment } from '../vision/faceIlluminationEngine';
export type { FaceIlluminationAssessment };

export interface MergerConflict {
  label: string;
  box: { x: number; y: number; width: number; height: number };
  evasionDirection: 'left' | 'right';
}

export interface DirectingRecommendation {
  priority: 'critical' | 'warning' | 'coaching' | 'locked';
  pillText: string | null;
  highlightEdge?: 'top' | 'bottom' | 'left' | 'right' | null;
  isOptimized: boolean;
  mergerConflict?: MergerConflict | null;
  illumination?: FaceIlluminationAssessment | null;
}

export interface CameraFocalState {
  zoomRatio: number;
  frameWidth?: number;
  frameHeight?: number;
}

export interface DirectingPoint {
  x: number;
  y: number;
  visibility?: number;
}

export interface NormalizedFaceRect {
  x: number;
  y: number;
  width: number;
  height: number;
  top?: number;
  left?: number;
}

function extractLandmarkArray(
  landmarks: any
): DirectingPoint[] | null {
  if (!landmarks) return null;
  if (Array.isArray(landmarks)) {
    if (landmarks.length === 0) return null;
    if (Array.isArray(landmarks[0])) return landmarks[0];
    if (landmarks[0]?.landmarks && Array.isArray(landmarks[0].landmarks)) {
      return landmarks[0].landmarks;
    }
    if (typeof landmarks[0]?.x === 'number') return landmarks;
  }
  if (landmarks?.landmarks && Array.isArray(landmarks.landmarks)) {
    return landmarks.landmarks;
  }
  return null;
}

function extractNormalizedFace(
  face: any,
  frameWidth?: number,
  frameHeight?: number
): { x: number; y: number; width: number; height: number; top: number } | null {
  if (!face) return null;
  const fw = frameWidth && frameWidth > 0 ? frameWidth : 1920;
  const fh = frameHeight && frameHeight > 0 ? frameHeight : 1080;

  const b = face.boundingBox ?? face;

  let rawX = b.originX !== undefined ? b.originX : (b.x !== undefined ? b.x : (b.left !== undefined ? b.left : 0));
  let rawY = b.originY !== undefined ? b.originY : (b.y !== undefined ? b.y : (b.top !== undefined ? b.top : 0));
  let rawW = b.width !== undefined ? b.width : 0;
  let rawH = b.height !== undefined ? b.height : 0;

  let x = rawX > 1 ? rawX / fw : rawX;
  let y = rawY > 1 ? rawY / fh : rawY;
  let width = rawW > 1 ? rawW / fw : rawW;
  let height = rawH > 1 ? rawH / fh : rawH;

  x = Math.max(0, Math.min(1, x));
  y = Math.max(0, Math.min(1, y));
  width = Math.max(0, Math.min(1, width));
  height = Math.max(0, Math.min(1, height));

  if (width < 0.02 || height < 0.02) {
    return null;
  }

  return { x, y, width, height, top: y };
}

function extractNormalizedObject(
  obj: any,
  frameWidth?: number,
  frameHeight?: number
): { label: string; score: number; x: number; y: number; width: number; height: number } | null {
  if (!obj) return null;
  const rawCat = obj.categories?.[0]?.categoryName || obj.label || '';
  const label = rawCat.toLowerCase().trim().replace(/_/g, ' ');
  const score = obj.categories?.[0]?.score ?? obj.score ?? obj.confidence ?? 1;

  let x = 0, y = 0, width = 0, height = 0;
  if (obj.boundingBox) {
    const b = obj.boundingBox;
    x = b.originX !== undefined ? b.originX : (b.x ?? 0);
    y = b.originY !== undefined ? b.originY : (b.y ?? 0);
    width = b.width ?? 0;
    height = b.height ?? 0;
  } else {
    x = obj.x ?? obj.left ?? 0;
    y = obj.y ?? obj.top ?? 0;
    width = obj.width ?? 0;
    height = obj.height ?? 0;
  }

  const fw = frameWidth && frameWidth > 0 ? frameWidth : 1920;
  const fh = frameHeight && frameHeight > 0 ? frameHeight : 1080;

  if (x > 1 || y > 1 || width > 1 || height > 1) {
    x /= fw;
    y /= fh;
    width /= fw;
    height /= fh;
  }

  return { label, score, x, y, width, height };
}

const WEARABLE_CLASSES = new Set(['clothing', 'tie', 'hat', 'sunglasses', 'glasses', 'suit', 'shirt']);

/**
 * Evaluates whether any salient background objects collide with or protrude from the subject's head zone.
 */
export function checkBackgroundMerger(
  face: { x: number; y: number; width: number; height: number; top: number },
  detectedObjects: any[] = [],
  frameWidth?: number,
  frameHeight?: number
): MergerConflict | null {
  if (!detectedObjects || detectedObjects.length === 0) return null;

  const faceCenterX = face.x + face.width / 2;

  // Vulnerable Head Zone: Includes crown/hair zone immediately above face where lamps/poles typically merge
  const headZone = {
    left: Math.max(0, faceCenterX - face.width * 0.75),
    right: Math.min(1, faceCenterX + face.width * 0.75),
    top: Math.max(0, face.top - 0.12),
    bottom: Math.min(1, face.top + face.height * 1.15),
  };

  for (const rawObj of detectedObjects) {
    const obj = extractNormalizedObject(rawObj, frameWidth, frameHeight);
    if (!obj) continue;

    // Filter out person class itself
    if (obj.label === 'person' || obj.label.includes('human')) continue;

    // Filter out wearable clothing / accessories on subject
    if (WEARABLE_CLASSES.has(obj.label)) continue;

    // Confidence check
    if (obj.score < 0.40) continue;

    // Salience filter: must be large enough to be an eye distraction, not the entire room wall
    const objArea = obj.width * obj.height;
    if (objArea < 0.012 || objArea > 0.45) continue;

    // Ignore if object is completely contained within the subject's face box
    if (
      obj.x >= face.x &&
      obj.x + obj.width <= face.x + face.width &&
      obj.y >= face.y &&
      obj.y + obj.height <= face.y + face.height
    ) {
      continue;
    }

    // Calculate intersection with headZone
    const interLeft = Math.max(obj.x, headZone.left);
    const interTop = Math.max(obj.y, headZone.top);
    const interRight = Math.min(obj.x + obj.width, headZone.right);
    const interBottom = Math.min(obj.y + obj.height, headZone.bottom);

    if (interRight > interLeft && interBottom > interTop) {
      const interArea = (interRight - interLeft) * (interBottom - interTop);
      // Minimum collision threshold (0.003 normalized area or >= 12% of object)
      if (interArea >= 0.003 || interArea / objArea >= 0.12) {
        const objCenterX = obj.x + obj.width / 2;
        const dx = objCenterX - faceCenterX;
        // Parallax evasion direction:
        // If obstacle is to the left or directly behind center-left (dx <= 0),
        // stepping right moves the foreground person left relative to the background!
        const evasionDirection: 'left' | 'right' = dx <= 0 ? 'right' : 'left';

        return {
          label: obj.label || 'obstacle',
          box: { x: obj.x, y: obj.y, width: obj.width, height: obj.height },
          evasionDirection,
        };
      }
    }
  }

  return null;
}

/**
 * Photographic Directing Engine
 * 
 * Strict Evaluation Hierarchy:
 * Level 1: Fatal Technical Flaws (Accidental joint crops, forehead clipping)
 * Level 1.5: Background Cleanliness & Mergers (Lamp/pole behind head)
 * Level 2: Lens & Perspective (Wide-angle distortion, bad camera distance)
 * Level 3: Natural Refinements (Headroom, look-room, quiet lock state)
 */
export function evaluatePhotographicDirecting(
  poseLandmarks: DirectingPoint[] | any | null | undefined,
  faceBoundingBox: NormalizedFaceRect | any | null | undefined,
  cameraFocalState: CameraFocalState = { zoomRatio: 1.0 },
  detectedObjects: any[] = [],
  illumination?: FaceIlluminationAssessment | null
): DirectingRecommendation {
  const fw = cameraFocalState?.frameWidth;
  const fh = cameraFocalState?.frameHeight;
  const points = extractLandmarkArray(poseLandmarks);
  const face = extractNormalizedFace(faceBoundingBox, fw, fh);
  const zoom = cameraFocalState?.zoomRatio ?? 1.0;

  // Extract pose anchors (nose and shoulders)
  const nose = points && points.length > 0 ? points[0] : null;
  const noseY = nose && (nose.visibility === undefined || nose.visibility >= 0.20) ? nose.y : null;

  const leftShoulder = points && points.length > 12 ? points[11] : null;
  const rightShoulder = points && points.length > 12 ? points[12] : null;
  const hasShoulderVis = (p: any) => p && (p.visibility === undefined || p.visibility > 0.20);
  const hasVisibleShoulders = hasShoulderVis(leftShoulder) || hasShoulderVis(rightShoulder);
  const minShoulderY = Math.min(
    hasShoulderVis(leftShoulder) && leftShoulder ? leftShoulder.y : 1,
    hasShoulderVis(rightShoulder) && rightShoulder ? rightShoulder.y : 1
  );

  // If a person's body/shoulders are visible in the upper/mid frame (minShoulderY < 0.70),
  // but the face has exited the top of the frame (face missing or nose <= 0.08)
  if (hasVisibleShoulders && minShoulderY < 0.70 && (!face || noseY === null || noseY <= 0.08)) {
    return {
      priority: 'critical',
      pillText: "Tilt up (head out of frame)",
      highlightEdge: 'top',
      mergerConflict: null,
      illumination: illumination ?? null,
      isOptimized: false,
    };
  }

  // If no subject is present in the frame, suppress advice (quiet camera)
  if (!face && (!points || points.length === 0)) {
    return {
      priority: 'coaching',
      pillText: null,
      highlightEdge: null,
      mergerConflict: null,
      illumination: illumination ?? null,
      isOptimized: false,
    };
  }

  // =========================================================================
  // LEVEL 1: Fatal Technical Flaws (Accidental Joint Crops & Forehead Clipping)
  // =========================================================================

  // 1. Forehead / Hair Headroom Check
  // Hair volume extends ~26% of face height (at least 0.06) above face.top (eyebrow line)
  const crownMargin = face ? Math.max(0.06, face.height * 0.26) : 0.08;
  const estimatedCrownY = face ? face.top - crownMargin : (noseY !== null ? noseY - 0.20 : null);

  // Severe Forehead / Crown Clipping (Critical)
  const isSevereHeadClip =
    (face && face.top <= 0.06) ||
    (estimatedCrownY !== null && estimatedCrownY <= -0.01) ||
    (noseY !== null && noseY <= 0.13);

  if (isSevereHeadClip) {
    return {
      priority: 'critical',
      pillText: "Tilt up slightly (protect headroom)",
      highlightEdge: 'top',
      mergerConflict: null,
      illumination: illumination ?? null,
      isOptimized: false,
    };
  }

  // Hair Grazing / Cutting into Top Edge (Warning)
  const isHairGrazing =
    (face && face.top < 0.12) ||
    (estimatedCrownY !== null && estimatedCrownY <= 0.03) ||
    (noseY !== null && noseY < 0.19);

  if (isHairGrazing) {
    return {
      priority: 'warning',
      pillText: "Tilt up slightly (protect headroom)",
      highlightEdge: 'top',
      mergerConflict: null,
      illumination: illumination ?? null,
      isOptimized: false,
    };
  }

  // 2. Ankle / Foot Crop Check
  // MediaPipe landmarks: leftAnkle (27), rightAnkle (28)
  // If either ankle Y is between 0.94 and 0.99, feet are grazed or awkwardly sliced
  if (points && points.length > 28) {
    const leftAnkle = points[27];
    const rightAnkle = points[28];

    const isAnkleClipping = (p?: DirectingPoint) => {
      if (!p) return false;
      if (p.visibility !== undefined && p.visibility < 0.25) return false;
      return p.y >= 0.94 && p.y <= 0.99;
    };

    if (isAnkleClipping(leftAnkle) || isAnkleClipping(rightAnkle)) {
      return {
        priority: 'critical',
        pillText: "Tilt down or include full feet",
        highlightEdge: 'bottom',
        mergerConflict: null,
        illumination: illumination ?? null,
        isOptimized: false,
      };
    }
  }

  // 3. Wrist / Hand Crop Check
  // MediaPipe landmarks: leftWrist (15), rightWrist (16)
  // If either wrist is clipping outer borders [0.00 to 0.03] or [0.97 to 1.00]
  if (points && points.length > 16) {
    const leftWrist = points[15];
    const rightWrist = points[16];

    const detectWristEdge = (p?: DirectingPoint): 'left' | 'right' | 'top' | 'bottom' | null => {
      if (!p) return null;
      if (p.visibility !== undefined && p.visibility < 0.25) return null;
      if (p.x >= 0.00 && p.x <= 0.03) return 'left';
      if (p.x >= 0.97 && p.x <= 1.00) return 'right';
      if (p.y >= 0.00 && p.y <= 0.03) return 'top';
      if (p.y >= 0.97 && p.y <= 1.00) return 'bottom';
      return null;
    };

    const edge = detectWristEdge(leftWrist) || detectWristEdge(rightWrist);
    if (edge) {
      return {
        priority: 'warning',
        pillText: "Step back to avoid cutting hands",
        highlightEdge: edge,
        mergerConflict: null,
        illumination: illumination ?? null,
        isOptimized: false,
      };
    }
  }

  // =========================================================================
  // LEVEL 1.5: Background Cleanliness & Mergers ("Lamp behind head")
  // =========================================================================
  if (face) {
    const merger = checkBackgroundMerger(face, detectedObjects, fw, fh);
    if (merger) {
      const dir = merger.evasionDirection;
      const label = merger.label;
      return {
        priority: 'warning',
        pillText: `Step ${dir} — ${label} behind head`,
        highlightEdge: dir,
        mergerConflict: merger,
        illumination: illumination ?? null,
        isOptimized: false,
      };
    }
  }

  // =========================================================================
  // LEVEL 1.8: Facial Illumination & Lighting (Split Shadows & Backlighting)
  // =========================================================================
  if (face && illumination) {
    if (illumination.quality === 'harsh_shadow') {
      return {
        priority: 'warning',
        pillText: illumination.actionNudgeText || "Turn face slightly toward the light",
        highlightEdge: null,
        mergerConflict: null,
        illumination,
        isOptimized: false,
      };
    }
    if (illumination.quality === 'backlit') {
      return {
        priority: 'warning',
        pillText: illumination.actionNudgeText || "Step out of backlighting",
        highlightEdge: null,
        mergerConflict: null,
        illumination,
        isOptimized: false,
      };
    }
  }

  // =========================================================================
  // LEVEL 2: Lens & Distance Coach (Perspective & Focal Distortion)
  // =========================================================================

  // 1. Barrel Facial Distortion Check (1x wide lens too close to face)
  if (face) {
    const faceArea = face.width * face.height;
    if (faceArea > 0.18 && zoom <= 1.0) {
      return {
        priority: 'coaching',
        pillText: "Step back & switch to 2x",
        highlightEdge: null,
        mergerConflict: null,
        illumination: illumination ?? null,
        isOptimized: false,
      };
    }
  }

  // 2. Full Body Scale Check
  // If full body is detected (shoulders 11, 12 down to ankles 27, 28) but height < 0.25
  if (points && points.length > 28) {
    const leftShoulder = points[11];
    const rightShoulder = points[12];
    const leftAnkle = points[27];
    const rightAnkle = points[28];

    const hasUpperBody =
      leftShoulder &&
      rightShoulder &&
      (leftShoulder.visibility === undefined || leftShoulder.visibility > 0.3) &&
      (rightShoulder.visibility === undefined || rightShoulder.visibility > 0.3);

    const hasLowerBody =
      leftAnkle &&
      rightAnkle &&
      (leftAnkle.visibility === undefined || leftAnkle.visibility > 0.3) &&
      (rightAnkle.visibility === undefined || rightAnkle.visibility > 0.3);

    if (hasUpperBody && hasLowerBody) {
      const topY = Math.min(leftShoulder.y, rightShoulder.y, points[0]?.y ?? 1);
      const bottomY = Math.max(leftAnkle.y, rightAnkle.y);
      const bodyHeight = bottomY - topY;

      if (bodyHeight > 0.05 && bodyHeight < 0.25) {
        return {
          priority: 'coaching',
          pillText: "Move closer to subject",
          highlightEdge: null,
          mergerConflict: null,
          illumination: illumination ?? null,
          isOptimized: false,
        };
      }
    }
  }

  // =========================================================================
  // LEVEL 3: Natural Refinements, Quiet Threshold & Lock State
  // =========================================================================

  // Headroom determination
  // For close-up and medium portraits, healthy headroom places face.top between 0.12 and 0.34
  const headroom = face ? face.top : noseY;

  if (headroom !== null) {
    // Healthy, balanced headroom for close-up and medium portraits triggers locked & ready state
    if (headroom >= 0.12 && headroom <= 0.34) {
      return {
        priority: 'locked',
        pillText: "Composition locked",
        highlightEdge: null,
        mergerConflict: null,
        illumination: illumination ?? null,
        isOptimized: true,
      };
    }

    // Excessive dead space above head: head is sinking below the lower third
    if (headroom > 0.38) {
      return {
        priority: 'coaching',
        pillText: "Tilt down slightly",
        highlightEdge: null,
        mergerConflict: null,
        illumination: illumination ?? null,
        isOptimized: false,
      };
    }
  }

  // Default quiet camera state: suppress trivial or low-confidence noise
  return {
    priority: 'coaching',
    pillText: null,
    highlightEdge: null,
    mergerConflict: null,
    illumination: illumination ?? null,
    isOptimized: false,
  };
}

