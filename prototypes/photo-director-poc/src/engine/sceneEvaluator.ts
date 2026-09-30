import type { DirectingRecommendation } from './photographicDirectingEngine';

export type DetectedSceneType = 'portrait' | 'tabletop_food' | 'scenic' | 'unknown';

export interface EdgeViolation {
  hasViolation: boolean;
  edges: ('top' | 'bottom' | 'left' | 'right')[];
  clutterDensity: number; // 0 to 1
}

export interface CompositionOpportunity {
  scene: DetectedSceneType;
  primarySubjectBox: { x: number; y: number; width: number; height: number } | null;
  targetAnchor: { x: number; y: number } | null; // Ideal focal position (e.g. rule of thirds 0.38 / 0.62)
  edgeViolation: EdgeViolation;
  actionNudge: {
    text: string;
    priority: 'low' | 'medium' | 'high';
  } | null;
  isOptimized: boolean; // True when framing is balanced and uncluttered
  recommendation?: DirectingRecommendation | null;
}

export interface NormalizedRect {
  x: number;
  y: number;
  width: number;
  height: number;
  centerX: number;
  centerY: number;
}

export const AMBIENT_CLASSES = new Set([
  'dining table',
  'table',
  'chair',
  'couch',
  'tv',
  'window',
  'refrigerator',
  'bed',
  'desk',
  'laptop',
  'computer',
  'monitor',
  'keyboard',
  'cell phone',
  'wall',
]);

const TABLEWARE_CLASSES = new Set([
  'cup',
  'bottle',
  'bowl',
  'plate',
  'wine glass',
  'fork',
  'knife',
  'spoon',
  'sandwich',
  'pizza',
  'donut',
  'cake',
]);

export function toNormalizedRect(
  box: { originX?: number; originY?: number; width?: number; height?: number; x?: number; y?: number },
  frameWidth: number,
  frameHeight: number
): NormalizedRect {
  const fw = frameWidth > 0 ? frameWidth : 1;
  const fh = frameHeight > 0 ? frameHeight : 1;

  let rawX = box.originX !== undefined ? box.originX : (box.x !== undefined ? box.x : 0);
  let rawY = box.originY !== undefined ? box.originY : (box.y !== undefined ? box.y : 0);
  let rawW = box.width !== undefined ? box.width : 0;
  let rawH = box.height !== undefined ? box.height : 0;

  // If pixel coordinates were passed, normalize them by frame dimensions
  if (rawX > 1 || rawY > 1 || rawW > 1 || rawH > 1) {
    rawX /= fw;
    rawY /= fh;
    rawW /= fw;
    rawH /= fh;
  }

  const x = Math.max(0, Math.min(1, rawX));
  const y = Math.max(0, Math.min(1, rawY));
  const width = Math.max(0, Math.min(1 - x, rawW));
  const height = Math.max(0, Math.min(1 - y, rawH));

  return {
    x,
    y,
    width,
    height,
    centerX: x + width / 2,
    centerY: y + height / 2,
  };
}

/**
 * Checks if a candidate box represents or significantly overlaps with the primary subject
 * (e.g. hero person's face/body or hero plate).
 */
export function isPrimarySubjectBox(
  box: NormalizedRect,
  primary: { x: number; y: number; width: number; height: number }
): boolean {
  // Direct bounds match within 5% tolerance
  const dx = Math.abs(box.x - primary.x);
  const dy = Math.abs(box.y - primary.y);
  const dw = Math.abs(box.width - primary.width);
  const dh = Math.abs(box.height - primary.height);
  if (dx < 0.05 && dy < 0.05 && dw < 0.05 && dh < 0.05) {
    return true;
  }

  // Calculate intersection rectangle
  const interLeft = Math.max(box.x, primary.x);
  const interTop = Math.max(box.y, primary.y);
  const interRight = Math.min(box.x + box.width, primary.x + primary.width);
  const interBottom = Math.min(box.y + box.height, primary.y + primary.height);

  if (interRight <= interLeft || interBottom <= interTop) {
    return false;
  }

  const interArea = (interRight - interLeft) * (interBottom - interTop);
  const boxArea = box.width * box.height;
  const primaryArea = primary.width * primary.height;

  // If candidate box overlaps significantly with primary subject (>= 35% of either area)
  if (boxArea > 0 && interArea / boxArea >= 0.35) return true;
  if (primaryArea > 0 && interArea / primaryArea >= 0.35) return true;

  // If candidate box contains the center of the primary subject (e.g., person torso containing face)
  const primaryCenterX = primary.x + primary.width / 2;
  const primaryCenterY = primary.y + primary.height / 2;
  if (
    primaryCenterX >= box.x &&
    primaryCenterX <= box.x + box.width &&
    primaryCenterY >= box.y &&
    primaryCenterY <= box.y + box.height
  ) {
    return true;
  }

  return false;
}

/**
 * Evaluates candidate objects against edge boundary criteria with strict false-positive suppression:
 * - Excludes primary subject (hero person/plate)
 * - Excludes ambient furniture/room classes (tables, chairs, beds, etc.)
 * - Applies Salience Filter (0.03 <= area <= 0.25)
 * - Requires confidence >= 0.55
 * - Strictly ignores bottom edge contact for portraits
 * - Edge violation trigger range: outer bound within [0, 0.04] or [0.96, 1.0]
 */
export function checkEdgeViolations(
  objects: any[] = [],
  primarySubjectBox: { x: number; y: number; width: number; height: number } | null,
  scene: DetectedSceneType,
  frameWidth: number = 1920,
  frameHeight: number = 1080
): EdgeViolation {
  const edgeSet = new Set<'top' | 'bottom' | 'left' | 'right'>();
  let violatingObjectCount = 0;
  let totalEvaluatedCount = 0;

  const validObjects = (Array.isArray(objects) ? objects : []).filter(
    (obj) => obj && (obj.boundingBox || (obj.width !== undefined && obj.height !== undefined))
  );

  for (const obj of validObjects) {
    // 1. High confidence requirement: score >= 0.55
    const confidence =
      obj.categories?.[0]?.score ??
      obj.score ??
      obj.confidence;
    if (confidence !== undefined && confidence < 0.55) {
      continue;
    }

    // 2. Ignore ambient environmental classes
    const rawCategory =
      obj.categories?.[0]?.categoryName ||
      obj.categories?.[0]?.displayName ||
      obj.label ||
      '';
    const categoryName = rawCategory.toLowerCase().trim().replace(/_/g, ' ');

    if (AMBIENT_CLASSES.has(categoryName)) {
      continue;
    }

    // In portraits, ignore person category (subject's torso or arms naturally exit frame edges)
    if (scene === 'portrait' && (categoryName === 'person' || categoryName.includes('human'))) {
      continue;
    }

    // Normalize candidate bounding box
    const box = toNormalizedRect(obj.boundingBox || obj, frameWidth, frameHeight);

    // 3. Filter out the primarySubjectBox (hero person or main plate)
    if (primarySubjectBox && isPrimarySubjectBox(box, primarySubjectBox)) {
      continue;
    }

    // 4. Salience Filter: normalized area must be between 0.03 and 0.25
    const area = box.width * box.height;
    if (area < 0.03 || area > 0.25) {
      continue;
    }

    totalEvaluatedCount++;

    // 5. Edge Boundary Definition:
    // Outer bound within normalized distance [0, 0.04] or [0.96, 1.0] of left, right, top edges.
    // For portraits, explicitly ignore bottom edge contact.
    let objectViolated = false;

    // Left edge
    if (box.x <= 0.04) {
      edgeSet.add('left');
      objectViolated = true;
    }

    // Right edge
    if (box.x + box.width >= 0.96) {
      edgeSet.add('right');
      objectViolated = true;
    }

    // Top edge
    if (box.y <= 0.04) {
      edgeSet.add('top');
      objectViolated = true;
    }

    // Bottom edge (explicitly ignored for portraits)
    if (scene !== 'portrait' && box.y + box.height >= 0.96) {
      edgeSet.add('bottom');
      objectViolated = true;
    }

    if (objectViolated) {
      violatingObjectCount++;
    }
  }

  // 6. Fallback: If no secondary clutter meets strict criteria, return clean state
  if (edgeSet.size === 0 || violatingObjectCount === 0) {
    return {
      hasViolation: false,
      edges: [],
      clutterDensity: 0,
    };
  }

  return {
    hasViolation: true,
    edges: Array.from(edgeSet),
    clutterDensity: Math.min(1, violatingObjectCount / Math.max(1, totalEvaluatedCount)),
  };
}

export function evaluateSceneComposition(
  faces: any[] = [],
  poses: any[] = [],
  objects: any[] = [],
  frameWidth: number = 1920,
  frameHeight: number = 1080,
  devicePitch: number = 0
): CompositionOpportunity {
  const validObjects = (Array.isArray(objects) ? objects : []).filter(
    (obj) => obj && (obj.boundingBox || (obj.width !== undefined && obj.height !== undefined))
  );

  const tablewareObjects = validObjects.filter((obj) => {
    const rawCategory = obj.categories?.[0]?.categoryName || obj.label || '';
    const category = rawCategory.toLowerCase().trim().replace(/_/g, ' ');
    return category && TABLEWARE_CLASSES.has(category);
  });

  const scene: DetectedSceneType = tablewareObjects.length > 0 ? 'tabletop_food' : 'unknown';

  let primarySubjectBox: { x: number; y: number; width: number; height: number } | null = null;
  let targetAnchor: { x: number; y: number } | null = null;
  let actionNudge: { text: string; priority: 'low' | 'medium' | 'high' } | null = null;
  let isOptimized = false;
  let totalArea = 0;

  if (scene === 'tabletop_food') {
    const targetTableware = tablewareObjects;
    let heroDishRect: NormalizedRect | null = null;
    let bestDishScore = -1;

    for (const obj of targetTableware) {
      const rect = toNormalizedRect(obj.boundingBox || obj, frameWidth, frameHeight);
      const area = rect.width * rect.height;
      const distCenter = Math.hypot(rect.centerX - 0.5, rect.centerY - 0.5);
      const dishScore = area * (1 - distCenter * 0.5);
      if (dishScore > bestDishScore) {
        bestDishScore = dishScore;
        heroDishRect = rect;
      }

      const interLeft = Math.max(rect.x, 0.25);
      const interTop = Math.max(rect.y, 0.25);
      const interRight = Math.min(rect.x + rect.width, 0.75);
      const interBottom = Math.min(rect.y + rect.height, 0.75);
      if (interRight > interLeft && interBottom > interTop) {
        totalArea += (interRight - interLeft) * (interBottom - interTop);
      }
    }

    if (heroDishRect) {
      primarySubjectBox = {
        x: heroDishRect.x,
        y: heroDishRect.y,
        width: heroDishRect.width,
        height: heroDishRect.height,
      };
    }
    
    // Default food anchor (center-ish)
    targetAnchor = { x: 0.5, y: 0.5 };
  }

  const edgeViolation = checkEdgeViolations(
    validObjects,
    primarySubjectBox,
    scene,
    frameWidth,
    frameHeight
  );

  // Evaluate Angle Constraints
  const absPitch = Math.abs(devicePitch);
  let isAngleOptimized = false;
  let angleNudge: string | null = null;

  // We want either ~90 (Flat Lay) or ~45 (Diner's View)
  if (absPitch >= 80 && absPitch <= 100) {
    isAngleOptimized = true; // Perfect flat lay
  } else if (absPitch > 65 && absPitch < 80) {
    angleNudge = "Tilt down more for flat lay (90°)";
  } else if (absPitch >= 40 && absPitch <= 50) {
    isAngleOptimized = true; // Perfect 45
  } else if (absPitch > 30 && absPitch < 40) {
    angleNudge = "Tilt up slightly for 45° angle";
  } else if (absPitch > 50 && absPitch <= 65) {
    angleNudge = "Tilt down slightly for 45° angle";
  }

  if (scene === 'tabletop_food') {
    if (primarySubjectBox && targetAnchor) {
      const subjectCenterX = primarySubjectBox.x + primarySubjectBox.width / 2;
      const subjectCenterY = primarySubjectBox.y + primarySubjectBox.height / 2;

      const dx = subjectCenterX - targetAnchor.x;
      const dy = subjectCenterY - targetAnchor.y;

      if (angleNudge) {
        actionNudge = { text: angleNudge, priority: 'high' };
      } else if (Math.abs(dx) > 0.10) {
        actionNudge = { text: dx > 0 ? 'Pan left' : 'Pan right', priority: 'high' };
      } else if (Math.abs(dy) > 0.10) {
        actionNudge = { text: dy > 0 ? 'Tilt up' : 'Tilt down', priority: 'high' };
      } else if (totalArea < 0.15) {
        actionNudge = { text: 'Move closer to the dish', priority: 'medium' };
      } else if (edgeViolation.hasViolation) {
        actionNudge = { text: 'Clear edge clutter', priority: 'high' };
      } else if (!isAngleOptimized) {
        actionNudge = { text: 'Adjust angle (Aim for 45° or 90°)', priority: 'medium' };
      } else {
        isOptimized = true;
        actionNudge = { text: 'Composition locked', priority: 'high' };
      }
    } else {
      actionNudge = { text: 'Finding dish...', priority: 'low' };
    }
  } else {
    actionNudge = { text: 'Point at food/tableware...', priority: 'low' };
  }

  if (!primarySubjectBox) {
    isOptimized = false;
  }

  return {
    scene,
    primarySubjectBox,
    targetAnchor,
    edgeViolation,
    actionNudge,
    isOptimized,
  };
}



