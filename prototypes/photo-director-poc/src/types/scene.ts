export interface SceneObject { label: string; score: number; left: number; top: number; width: number; height: number; isConflict: boolean }
export type SceneTone = "good" | "caution" | "problem";
export interface SceneAssessment {
  brightness: number; highlightClip: number; shadowClip: number; edgeDensity: number; subjectContrast: number;
  lightTone: SceneTone; backgroundTone: SceneTone; separationTone: SceneTone;
  objects: SceneObject[];
}
