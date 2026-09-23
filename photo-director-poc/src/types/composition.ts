/**
 * Core TypeScript definitions for the multi-genre composition engine.
 */

// ============================================================================
// Multi-Genre Composition Types
// ============================================================================

/**
 * Supported composition genre presets for photographic direction.
 */
export type GenrePreset = 'PEOPLE' | 'FOOD' | 'NATURE' | 'STREET' | 'MACRO';

/**
 * Normalized bounding box coordinates within the camera viewfinder (0.0 to 1.0).
 * - x: Normalized horizontal coordinate of the top-left corner (0.0 = left edge, 1.0 = right edge)
 * - y: Normalized vertical coordinate of the top-left corner (0.0 = top edge, 1.0 = bottom edge)
 * - width: Normalized width of the bounding box (0.0 to 1.0)
 * - height: Normalized height of the bounding box (0.0 to 1.0)
 */
export interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * State of composition evaluation indicating directional user correction or readiness.
 */
export type RuleEvaluationState =
  | 'READY'
  | 'MOVE_LEFT'
  | 'MOVE_RIGHT'
  | 'MOVE_UP'
  | 'MOVE_DOWN'
  | 'MOVE_CLOSER'
  | 'MOVE_BACK'
  | 'NO_SUBJECT';

/**
 * Active visual guideline overlay type.
 */
export type ActiveGuideline = 'THIRDS' | 'OVERHEAD_CROSSHAIR' | 'HORIZON';

/**
 * Evaluation result returned by the multi-genre composition rule engine.
 */
export interface RuleEvaluationResult {
  /** Evaluation state directing camera repositioning or indicating readiness */
  state: RuleEvaluationState;
  /** 2–4 word actionable GPS-style nudges (e.g., "Raise phone slightly", "Lower to 45° angle", "Step back") */
  guidanceText: string;
  /** Target zone within the viewfinder defined in normalized 0.0-1.0 coordinates */
  targetZone: BoundingBox;
  /** Whether the subject/composition is aligned (triggers the lime-yellow `#d5ff48` glow state) */
  isAligned: boolean;
  /** Optional active guideline geometry overlay */
  activeGuideline?: ActiveGuideline;
}

/**
 * Device orientation data captured from hardware sensors (gyroscope/accelerometer).
 */
export interface DeviceOrientation {
  /** Pitch angle in degrees (device tilt up/down relative to horizon) */
  pitch: number;
  /** Roll angle in degrees (device lateral tilt / leveling) */
  roll: number;
}

// ============================================================================
// Vision & Subject Observation Types (Existing Pipeline Compatibility)
// ============================================================================

export interface DetectedSubject {
  centerX: number;
  centerY: number;
  width: number;
  height: number;
  confidence?: number;
}

export type FramingMode = 'AUTO' | 'CLOSE_UP' | 'WAIST_UP' | 'FULL_BODY';

export interface BodyObservation {
  centerX: number;
  centerY: number;
  width: number;
  height: number;
  shouldersVisible: boolean;
  hipsVisible: boolean;
  kneesVisible: boolean;
  anklesVisible: boolean;
  top: number;
  bottom: number;
  left: number;
  right: number;
}

export interface SceneObservation {
  face: DetectedSubject | null;
  body: BodyObservation | null;
  faceCount: number;
}

export type GuidanceType =
  | 'MOVE_LEFT'
  | 'MOVE_RIGHT'
  | 'MOVE_UP'
  | 'MOVE_DOWN'
  | 'MOVE_CLOSER'
  | 'MOVE_BACK'
  | 'READY'
  | 'NO_SUBJECT';

export interface CompositionGuidance {
  type: GuidanceType;
  message: string;
  severity: number;
  reason?: string;
}

export interface ArmaturePoint {
  x: number;
  y: number;
  label?: string;
}

export interface ArmatureConfig {
  id: string;
  preset: GenrePreset | string;
  name: string;
  targetPoints: ArmaturePoint[]; // Normalized 0-1 coordinates
  guideType: 'triangle' | 'diagonal' | 'rule_of_thirds' | 'horizon';
  isAligned: boolean;
}

export type GuidanceNudge = {
  text: string;
  severity: 'info' | 'ready' | 'warning';
};
