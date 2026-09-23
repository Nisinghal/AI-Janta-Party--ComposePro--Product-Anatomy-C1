import type {
  GenrePreset,
  BoundingBox,
  RuleEvaluationResult,
  RuleEvaluationState,
} from '../types/composition';

// ============================================================================
// Target Zone Blueprint Helpers
// ============================================================================

const PEOPLE_TARGET_ZONE: BoundingBox = {
  x: 0.22,
  y: 0.11,
  width: 0.32,
  height: 0.46,
};

const FOOD_FLATLAY_TARGET_ZONE: BoundingBox = {
  x: 0.25,
  y: 0.25,
  width: 0.50,
  height: 0.50,
};

const FOOD_PERSPECTIVE_TARGET_ZONE: BoundingBox = {
  x: 0.25,
  y: 0.45,
  width: 0.50,
  height: 0.40,
};

const STREET_TARGET_ZONE: BoundingBox = {
  x: 0.30,
  y: 0.20,
  width: 0.40,
  height: 0.60,
};

const MACRO_TARGET_ZONE: BoundingBox = {
  x: 0.25,
  y: 0.25,
  width: 0.50,
  height: 0.50,
};

function getNatureTargetZone(targetX: number): BoundingBox {
  return {
    x: Math.max(0, targetX - 0.15),
    y: 0.52,
    width: 0.30,
    height: 0.30,
  };
}

// ============================================================================
// Preset Rule Evaluators
// ============================================================================

/**
 * PEOPLE:
 * - Target face/head center at x=0.38 (rule of thirds), eye-line at y=0.34
 * - If height < 0.35 -> 'MOVE_CLOSER' ("Step closer")
 * - If height > 0.85 -> 'MOVE_BACK' ("Step back 2 steps")
 * - Tolerance window: |dx| <= 0.08 and |dy| <= 0.06 -> 'READY' ("Perfect angle", isAligned = true, guideline = 'THIRDS')
 */
function evaluatePeople(subjectBox: BoundingBox | null): RuleEvaluationResult {
  if (!subjectBox) {
    return {
      state: 'NO_SUBJECT',
      guidanceText: 'Point camera at person',
      targetZone: PEOPLE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  // Check scale / distance constraints first
  if (subjectBox.height < 0.35) {
    return {
      state: 'MOVE_CLOSER',
      guidanceText: 'Step closer',
      targetZone: PEOPLE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (subjectBox.height > 0.85) {
    return {
      state: 'MOVE_BACK',
      guidanceText: 'Step back 2 steps',
      targetZone: PEOPLE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  const centerX = subjectBox.x + subjectBox.width / 2;
  const centerY = subjectBox.y + subjectBox.height / 2;
  const dx = centerX - 0.38;
  const dy = centerY - 0.34;

  // Tolerance window: |dx| <= 0.08 and |dy| <= 0.06
  if (Math.abs(dx) <= 0.08 && Math.abs(dy) <= 0.06) {
    return {
      state: 'READY',
      guidanceText: 'Perfect angle',
      targetZone: PEOPLE_TARGET_ZONE,
      isAligned: true,
      activeGuideline: 'THIRDS',
    };
  }

  // Directional guidance outside tolerance window
  if (dx < -0.08) {
    return {
      state: 'MOVE_LEFT',
      guidanceText: 'Pan left',
      targetZone: PEOPLE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (dx > 0.08) {
    return {
      state: 'MOVE_RIGHT',
      guidanceText: 'Pan right',
      targetZone: PEOPLE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (dy < -0.06) {
    return {
      state: 'MOVE_UP',
      guidanceText: 'Raise phone slightly',
      targetZone: PEOPLE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  return {
    state: 'MOVE_DOWN',
    guidanceText: 'Lower phone slightly',
    targetZone: PEOPLE_TARGET_ZONE,
    isAligned: false,
    activeGuideline: 'THIRDS',
  };
}

/**
 * FOOD:
 * - If deviceTilt is available and pitch > 75°: Flatlay mode.
 *   If |pitch - 90| < 3° and |roll| < 3° -> 'READY' ("Level flatlay", isAligned = true, guideline = 'OVERHEAD_CROSSHAIR')
 *   else "Hold parallel to table".
 * - Default: 45° perspective. Target hero plate center at y=0.65 (lower third).
 *   If center y < 0.55 -> 'MOVE_DOWN' ("Lower to 45° angle").
 *   If width < 0.40 -> 'MOVE_CLOSER' ("Move closer to dish").
 */
function evaluateFood(
  subjectBox: BoundingBox | null,
  deviceTilt?: { pitch: number; roll: number }
): RuleEvaluationResult {
  // Flatlay mode triggered by overhead pitch > 75°
  if (deviceTilt !== undefined && deviceTilt.pitch > 75) {
    const isLevel = Math.abs(deviceTilt.pitch - 90) < 3 && Math.abs(deviceTilt.roll) < 3;

    if (isLevel) {
      return {
        state: 'READY',
        guidanceText: 'Level flatlay',
        targetZone: FOOD_FLATLAY_TARGET_ZONE,
        isAligned: true,
        activeGuideline: 'OVERHEAD_CROSSHAIR',
      };
    }

    // Directional tilt correction to achieve level flatlay
    let tiltState: RuleEvaluationState = 'MOVE_DOWN';
    if (deviceTilt.pitch < 87) {
      tiltState = 'MOVE_DOWN';
    } else if (deviceTilt.pitch > 93) {
      tiltState = 'MOVE_UP';
    } else if (deviceTilt.roll > 3) {
      tiltState = 'MOVE_LEFT';
    } else if (deviceTilt.roll < -3) {
      tiltState = 'MOVE_RIGHT';
    }

    return {
      state: tiltState,
      guidanceText: 'Hold parallel to table',
      targetZone: FOOD_FLATLAY_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'OVERHEAD_CROSSHAIR',
    };
  }

  // Default: 45° perspective
  if (!subjectBox) {
    return {
      state: 'NO_SUBJECT',
      guidanceText: 'Frame hero plate',
      targetZone: FOOD_PERSPECTIVE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  const centerY = subjectBox.y + subjectBox.height / 2;

  if (centerY < 0.55) {
    return {
      state: 'MOVE_DOWN',
      guidanceText: 'Lower to 45° angle',
      targetZone: FOOD_PERSPECTIVE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (subjectBox.width < 0.40) {
    return {
      state: 'MOVE_CLOSER',
      guidanceText: 'Move closer to dish',
      targetZone: FOOD_PERSPECTIVE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (subjectBox.width > 0.88) {
    return {
      state: 'MOVE_BACK',
      guidanceText: 'Step back slightly',
      targetZone: FOOD_PERSPECTIVE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (centerY > 0.80) {
    return {
      state: 'MOVE_UP',
      guidanceText: 'Raise phone slightly',
      targetZone: FOOD_PERSPECTIVE_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  // Lower-third hero dish aligned
  return {
    state: 'READY',
    guidanceText: 'Hero plate aligned',
    targetZone: FOOD_PERSPECTIVE_TARGET_ZONE,
    isAligned: true,
    activeGuideline: 'THIRDS',
  };
}

/**
 * NATURE:
 * - Anchor subject to nearest horizontal third (x=0.33 or x=0.67) and lower third (y=0.67)
 * - If aligned within 10% -> 'READY' ("Horizon aligned", isAligned = true, guideline = 'HORIZON')
 */
function evaluateNature(subjectBox: BoundingBox | null): RuleEvaluationResult {
  if (!subjectBox) {
    return {
      state: 'NO_SUBJECT',
      guidanceText: 'Frame landscape horizon',
      targetZone: getNatureTargetZone(0.33),
      isAligned: false,
      activeGuideline: 'HORIZON',
    };
  }

  const centerX = subjectBox.x + subjectBox.width / 2;
  const centerY = subjectBox.y + subjectBox.height / 2;

  // Determine closest horizontal third anchor (0.33 or 0.67)
  const targetX = Math.abs(centerX - 0.33) <= Math.abs(centerX - 0.67) ? 0.33 : 0.67;
  const targetY = 0.67;
  const targetZone = getNatureTargetZone(targetX);

  const dx = Math.abs(centerX - targetX);
  const dy = Math.abs(centerY - targetY);

  // Aligned within 10% margin
  if (dx <= 0.10 && dy <= 0.10) {
    return {
      state: 'READY',
      guidanceText: 'Horizon aligned',
      targetZone,
      isAligned: true,
      activeGuideline: 'HORIZON',
    };
  }

  // Vertical placement priority (lower third)
  if (centerY < targetY - 0.10) {
    return {
      state: 'MOVE_DOWN',
      guidanceText: 'Lower to lower third',
      targetZone,
      isAligned: false,
      activeGuideline: 'HORIZON',
    };
  }

  if (centerY > targetY + 0.10) {
    return {
      state: 'MOVE_UP',
      guidanceText: 'Raise phone slightly',
      targetZone,
      isAligned: false,
      activeGuideline: 'HORIZON',
    };
  }

  // Horizontal placement toward closest third
  if (centerX < targetX - 0.10) {
    return {
      state: 'MOVE_LEFT',
      guidanceText: 'Pan left to third',
      targetZone,
      isAligned: false,
      activeGuideline: 'HORIZON',
    };
  }

  return {
    state: 'MOVE_RIGHT',
    guidanceText: 'Pan right to third',
    targetZone,
    isAligned: false,
    activeGuideline: 'HORIZON',
  };
}

/**
 * STREET:
 * - Keystoning & vanishing perspective:
 *   If height > 0.92 -> 'MOVE_BACK' ("Step back to keep walls vertical")
 *   Center vanishing point horizontally within 12% margin -> 'READY' ("Lines straight", guideline = 'THIRDS')
 */
function evaluateStreet(subjectBox: BoundingBox | null): RuleEvaluationResult {
  if (!subjectBox) {
    return {
      state: 'NO_SUBJECT',
      guidanceText: 'Align street perspective',
      targetZone: STREET_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  // Keystoning check: subject occupying almost full vertical height distorts vertical lines
  if (subjectBox.height > 0.92) {
    return {
      state: 'MOVE_BACK',
      guidanceText: 'Step back to keep walls vertical',
      targetZone: STREET_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  const centerX = subjectBox.x + subjectBox.width / 2;
  const dxFromCenter = Math.abs(centerX - 0.50);

  // Center vanishing point horizontally within 12% margin (0.38 <= centerX <= 0.62)
  if (dxFromCenter <= 0.12) {
    return {
      state: 'READY',
      guidanceText: 'Lines straight',
      targetZone: STREET_TARGET_ZONE,
      isAligned: true,
      activeGuideline: 'THIRDS',
    };
  }

  if (centerX < 0.38) {
    return {
      state: 'MOVE_LEFT',
      guidanceText: 'Pan left to center',
      targetZone: STREET_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  return {
    state: 'MOVE_RIGHT',
    guidanceText: 'Pan right to center',
    targetZone: STREET_TARGET_ZONE,
    isAligned: false,
    activeGuideline: 'THIRDS',
  };
}

/**
 * MACRO:
 * - Target subject area (width * height) between 20% and 70% of frame
 * - If < 0.20 -> "Move closer for detail"
 * - If background edge density > 0.65 -> "Shift angle to clean background"
 */
function evaluateMacro(
  subjectBox: BoundingBox | null,
  edgeDensity: number
): RuleEvaluationResult {
  if (!subjectBox) {
    return {
      state: 'NO_SUBJECT',
      guidanceText: 'Move closer to subject',
      targetZone: MACRO_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  const area = subjectBox.width * subjectBox.height;

  if (area < 0.20) {
    return {
      state: 'MOVE_CLOSER',
      guidanceText: 'Move closer for detail',
      targetZone: MACRO_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (area > 0.70) {
    return {
      state: 'MOVE_BACK',
      guidanceText: 'Step back for focus',
      targetZone: MACRO_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  // Background clutter check via 48x48 background analyzer edge density
  if (edgeDensity > 0.65) {
    return {
      state: 'MOVE_RIGHT',
      guidanceText: 'Shift angle to clean background',
      targetZone: MACRO_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  return {
    state: 'READY',
    guidanceText: 'Macro focus locked',
    targetZone: MACRO_TARGET_ZONE,
    isAligned: true,
    activeGuideline: 'THIRDS',
  };
}

// ============================================================================
// Core Pure Geometric Rule Engine Entry Point
// ============================================================================

/**
 * Pure geometric rule engine for multi-genre photographic composition.
 * Evaluates bounding box coordinates, edge density, and hardware orientation
 * with 0 external API calls and zero latency.
 *
 * @param preset - Active photographic genre preset ('PEOPLE' | 'FOOD' | 'NATURE' | 'STREET' | 'MACRO')
 * @param subjectBox - Normalized bounding box of the detected subject/face/hero object (0.0 to 1.0)
 * @param edgeDensity - Normalized edge density (0.0 to 1.0) from background analyzer
 * @param deviceTilt - Optional hardware orientation telemetry { pitch, roll } in degrees
 * @returns RuleEvaluationResult with actionable GPS-style nudges, target zone, and alignment state
 */
export function evaluateComposition(
  preset: GenrePreset,
  subjectBox: BoundingBox | null,
  edgeDensity: number,
  deviceTilt?: { pitch: number; roll: number }
): RuleEvaluationResult {
  const normalizedPreset = (typeof preset === 'string'
    ? preset.toUpperCase()
    : preset) as GenrePreset;

  switch (normalizedPreset) {
    case 'PEOPLE':
      return evaluatePeople(subjectBox);

    case 'FOOD':
      return evaluateFood(subjectBox, deviceTilt);

    case 'NATURE':
      return evaluateNature(subjectBox);

    case 'STREET':
      return evaluateStreet(subjectBox);

    case 'MACRO':
      return evaluateMacro(subjectBox, edgeDensity);

    default:
      return {
        state: 'NO_SUBJECT',
        guidanceText: 'Select composition preset',
        targetZone: PEOPLE_TARGET_ZONE,
        isAligned: false,
      };
  }
}
