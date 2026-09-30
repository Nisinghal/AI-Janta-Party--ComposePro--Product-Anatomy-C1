import { compositionConfig as c } from "./compositionConfig";
import type { CompositionGuidance, DetectedSubject } from "../types/composition";
export function smoothSubject(previous: DetectedSubject | null, current: DetectedSubject): DetectedSubject {
  if (!previous) return current;
  const mix = (a: number, b: number) => c.smoothingAlpha * b + (1 - c.smoothingAlpha) * a;
  return { centerX: mix(previous.centerX, current.centerX), centerY: mix(previous.centerY, current.centerY), width: mix(previous.width, current.width), height: mix(previous.height, current.height), confidence: current.confidence };
}
export interface StabilizerState { visible: CompositionGuidance; candidate: CompositionGuidance; candidateSince: number }
export function stabilizeGuidance(state: StabilizerState, next: CompositionGuidance, now: number): StabilizerState {
  if (next.type === "NO_SUBJECT") return { visible: next, candidate: next, candidateSince: now };
  if (next.type === state.visible.type) return { visible: next, candidate: next, candidateSince: state.candidateSince };
  if (next.type !== state.candidate.type) return { ...state, candidate: next, candidateSince: now };
  const wait = next.type === "READY" ? c.readyConfirmationMs : c.instructionPersistenceMs;
  return next.type !== state.visible.type && now - state.candidateSince >= wait ? { visible: next, candidate: next, candidateSince: state.candidateSince } : { ...state, candidate: next };
}
