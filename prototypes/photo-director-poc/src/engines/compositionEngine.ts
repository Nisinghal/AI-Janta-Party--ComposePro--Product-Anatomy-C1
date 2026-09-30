import type {
  GenrePreset,
  BoundingBox,
  RuleEvaluationResult,
  RuleEvaluationState,
} from '../types/composition';

// ============================================================================
// Target Zone Blueprint Helpers
// ============================================================================

const FOOD_FLATLAY_TARGET_ZONE: BoundingBox = {
  x: 0.33,
  y: 0.33,
  width: 0.33,
  height: 0.33,
};

const FOOD_HEROSHOT_TARGET_ZONE: BoundingBox = {
  x: 0.33,
  y: 0.45,
  width: 0.33,
  height: 0.40,
};

const FOOD_TALLSTACK_TARGET_ZONE: BoundingBox = {
  x: 0.33,
  y: 0.20,
  width: 0.33,
  height: 0.60,
};

// ============================================================================
// Preset Rule Evaluators
// ============================================================================

function checkNegativeSpace(subjectBox: BoundingBox): RuleEvaluationState | null {
  const area = subjectBox.width * subjectBox.height;
  if (area > 0.80) {
    return 'MOVE_BACK';
  }
  return null;
}

function evaluateFlatLay(
  subjectBox: BoundingBox | null,
  deviceTilt?: { pitch: number; roll: number }
): RuleEvaluationResult {
  if (!subjectBox) {
    return {
      state: 'NO_SUBJECT',
      guidanceText: 'Point at food for flat lay',
      targetZone: FOOD_FLATLAY_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'OVERHEAD_CROSSHAIR',
    };
  }

  const spaceCheck = checkNegativeSpace(subjectBox);
  if (spaceCheck === 'MOVE_BACK') {
    return {
      state: 'MOVE_BACK',
      guidanceText: 'Move back to create breathing room',
      targetZone: FOOD_FLATLAY_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'OVERHEAD_CROSSHAIR',
    };
  }

  if (deviceTilt !== undefined) {
    const isLevel = Math.abs(deviceTilt.pitch - 90) < 5 && Math.abs(deviceTilt.roll) < 5;
    if (isLevel) {
      return {
        state: 'READY',
        guidanceText: 'Level flat lay',
        targetZone: FOOD_FLATLAY_TARGET_ZONE,
        isAligned: true,
        activeGuideline: 'OVERHEAD_CROSSHAIR',
      };
    }

    let tiltState: RuleEvaluationState = 'MOVE_DOWN';
    if (deviceTilt.pitch < 85) {
      tiltState = 'MOVE_DOWN';
    } else if (deviceTilt.pitch > 95) {
      tiltState = 'MOVE_UP';
    } else if (deviceTilt.roll > 5) {
      tiltState = 'MOVE_LEFT';
    } else if (deviceTilt.roll < -5) {
      tiltState = 'MOVE_RIGHT';
    }

    return {
      state: tiltState,
      guidanceText: 'Hold parallel to table (90°)',
      targetZone: FOOD_FLATLAY_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'OVERHEAD_CROSSHAIR',
    };
  }
  
  return {
    state: 'READY',
    guidanceText: 'Flat lay ready',
    targetZone: FOOD_FLATLAY_TARGET_ZONE,
    isAligned: true,
    activeGuideline: 'OVERHEAD_CROSSHAIR',
  };
}

function evaluateHeroShot(
  subjectBox: BoundingBox | null,
  deviceTilt?: { pitch: number; roll: number }
): RuleEvaluationResult {
  if (!subjectBox) {
    return {
      state: 'NO_SUBJECT',
      guidanceText: 'Point at food for hero shot',
      targetZone: FOOD_HEROSHOT_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  const spaceCheck = checkNegativeSpace(subjectBox);
  if (spaceCheck === 'MOVE_BACK') {
    return {
      state: 'MOVE_BACK',
      guidanceText: 'Move back to create breathing room',
      targetZone: FOOD_HEROSHOT_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (deviceTilt !== undefined) {
    if (deviceTilt.pitch < 40) {
      return {
        state: 'MOVE_UP',
        guidanceText: 'Raise for 45° angle',
        targetZone: FOOD_HEROSHOT_TARGET_ZONE,
        isAligned: false,
        activeGuideline: 'THIRDS',
      };
    }
    if (deviceTilt.pitch > 65) {
      return {
        state: 'MOVE_DOWN',
        guidanceText: 'Lower to 45° angle',
        targetZone: FOOD_HEROSHOT_TARGET_ZONE,
        isAligned: false,
        activeGuideline: 'THIRDS',
      };
    }
  }

  const centerY = subjectBox.y + subjectBox.height / 2;
  if (centerY < 0.45) {
    return {
      state: 'MOVE_DOWN',
      guidanceText: 'Lower camera slightly',
      targetZone: FOOD_HEROSHOT_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }
  if (centerY > 0.75) {
    return {
      state: 'MOVE_UP',
      guidanceText: 'Raise camera slightly',
      targetZone: FOOD_HEROSHOT_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  return {
    state: 'READY',
    guidanceText: 'Hero plate aligned',
    targetZone: FOOD_HEROSHOT_TARGET_ZONE,
    isAligned: true,
    activeGuideline: 'THIRDS',
  };
}

function evaluateTallStack(
  subjectBox: BoundingBox | null,
  deviceTilt?: { pitch: number; roll: number }
): RuleEvaluationResult {
  if (!subjectBox) {
    return {
      state: 'NO_SUBJECT',
      guidanceText: 'Point at food for tall stack',
      targetZone: FOOD_TALLSTACK_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  const spaceCheck = checkNegativeSpace(subjectBox);
  if (spaceCheck === 'MOVE_BACK') {
    return {
      state: 'MOVE_BACK',
      guidanceText: 'Move back to create breathing room',
      targetZone: FOOD_TALLSTACK_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  if (deviceTilt !== undefined) {
    if (deviceTilt.pitch > 20) {
      return {
        state: 'MOVE_DOWN',
        guidanceText: 'Shoot straight on (0°)',
        targetZone: FOOD_TALLSTACK_TARGET_ZONE,
        isAligned: false,
        activeGuideline: 'THIRDS',
      };
    }
  }

  const centerX = subjectBox.x + subjectBox.width / 2;
  if (centerX < 0.4) {
    return {
      state: 'MOVE_LEFT',
      guidanceText: 'Center the stack',
      targetZone: FOOD_TALLSTACK_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }
  if (centerX > 0.6) {
    return {
      state: 'MOVE_RIGHT',
      guidanceText: 'Center the stack',
      targetZone: FOOD_TALLSTACK_TARGET_ZONE,
      isAligned: false,
      activeGuideline: 'THIRDS',
    };
  }

  return {
    state: 'READY',
    guidanceText: 'Stack aligned',
    targetZone: FOOD_TALLSTACK_TARGET_ZONE,
    isAligned: true,
    activeGuideline: 'THIRDS',
  };
}

export function evaluateComposition(
  preset: GenrePreset,
  subjectBox: BoundingBox | null,
  edgeDensity: number,
  deviceTilt?: { pitch: number; roll: number }
): RuleEvaluationResult {
  const normalizedPreset = (typeof preset === 'string' ? preset.toUpperCase() : preset) as GenrePreset;

  switch (normalizedPreset) {
    case 'FLAT_LAY':
      return evaluateFlatLay(subjectBox, deviceTilt);
    case 'HERO_SHOT':
      return evaluateHeroShot(subjectBox, deviceTilt);
    case 'TALL_STACK':
      return evaluateTallStack(subjectBox, deviceTilt);
    default:
      return {
        state: 'NO_SUBJECT',
        guidanceText: 'Select composition preset',
        targetZone: FOOD_HEROSHOT_TARGET_ZONE,
        isAligned: false,
      };
  }
}
