"use client";

import React, { createContext, useContext, useMemo, useState, useCallback } from "react";
import type {
  GenrePreset,
  ArmaturePoint,
  ArmatureConfig,
  GuidanceNudge,
  RuleEvaluationResult,
} from "../types/composition";
import type { CompositionOpportunity } from "../engine/sceneEvaluator";
import type { DirectingRecommendation } from "../engine/photographicDirectingEngine";

export interface PresetBlueprint {
  id: string;
  name: string;
  guideType: 'triangle' | 'diagonal' | 'rule_of_thirds' | 'horizon';
  targetPoints: ArmaturePoint[];
  defaultNudge: string;
}

export const PRESET_CONFIGS: Record<GenrePreset, PresetBlueprint> = {
  PEOPLE: {
    id: "preset-people",
    name: "Dynamic Portrait / People",
    guideType: "diagonal",
    targetPoints: [
      { x: 0.38, y: 0.35, label: "eye-line" },
      { x: 0.65, y: 0.68, label: "body-axis" },
    ],
    defaultNudge: "Align eye-line along y: 0.35 and angle body on the diagonal",
  },
  FOOD: {
    id: "preset-food",
    name: "Culinary Triangle",
    guideType: "triangle",
    targetPoints: [
      { x: 0.5, y: 0.35, label: "pyramid-apex" },
      { x: 0.28, y: 0.72, label: "pyramid-left" },
      { x: 0.72, y: 0.72, label: "pyramid-right" },
    ],
    defaultNudge: "Frame subject dishes within visual pyramid vertices",
  },
  NATURE: {
    id: "preset-nature",
    name: "Horizon & Rule of Thirds",
    guideType: "rule_of_thirds",
    targetPoints: [
      { x: 0.5, y: 0.33, label: "sky-split" },
      { x: 0.5, y: 0.66, label: "ground-split" },
      { x: 0.33, y: 0.33, label: "third-top-left" },
      { x: 0.66, y: 0.33, label: "third-top-right" },
      { x: 0.33, y: 0.66, label: "third-bottom-left" },
      { x: 0.66, y: 0.66, label: "third-bottom-right" },
    ],
    defaultNudge: "Align horizon along sky/ground thirds split",
  },
  STREET: {
    id: "preset-street",
    name: "Street & Architecture",
    guideType: "horizon",
    targetPoints: [
      { x: 0.25, y: 0.5, label: "keystone-left" },
      { x: 0.75, y: 0.5, label: "keystone-right" },
      { x: 0.5, y: 0.5, label: "horizon-center" },
    ],
    defaultNudge: "Align vertical structures with keystone guides at x: 0.25 and 0.75",
  },
  MACRO: {
    id: "preset-macro",
    name: "Macro & Close-Up",
    guideType: "triangle",
    targetPoints: [
      { x: 0.5, y: 0.5, label: "macro-focus" },
    ],
    defaultNudge: "Center subject in macro target zone",
  },
};

import type { FaceIlluminationAssessment } from "../vision/faceIlluminationEngine";

export interface CompositionContextValue {
  activePreset: GenrePreset;
  setActivePreset: (preset: GenrePreset) => void;
  activeArmature: ArmatureConfig;
  guidanceNudge: GuidanceNudge;
  isAligned: boolean;
  opportunity: CompositionOpportunity | null;
  recommendation: DirectingRecommendation | null;
  illumination: FaceIlluminationAssessment | null;
  ruleResult: RuleEvaluationResult | null;
  setDetectionTargets: (points: ArmaturePoint[]) => void;
  setAnalysisResult: (isAligned: boolean, nudge: GuidanceNudge) => void;
  setOpportunity: (opp: CompositionOpportunity | null) => void;
  setRuleResult: (result: RuleEvaluationResult | null) => void;
}

export const CompositionContext = createContext<CompositionContextValue | undefined>(undefined);

export function CompositionProvider({ children }: { children: React.ReactNode }) {
  const [activePreset, setActivePreset] = useState<GenrePreset>("PEOPLE");
  const [detectedTargets, setDetectedTargets] = useState<ArmaturePoint[]>([]);
  const [opportunity, setOpportunity] = useState<CompositionOpportunity | null>(null);
  const [ruleResult, setRuleResult] = useState<RuleEvaluationResult | null>(null);
  const [analysisOverride, setAnalysisOverride] = useState<{
    isAligned: boolean;
    guidanceNudge: GuidanceNudge;
  } | null>(null);

  const handleSetDetectionTargets = useCallback((points: ArmaturePoint[]) => {
    setDetectedTargets(points);
  }, []);

  const handleSetAnalysisResult = useCallback((isAligned: boolean, nudge: GuidanceNudge) => {
    setAnalysisOverride({ isAligned, guidanceNudge: nudge });
  }, []);

  const computedAlignment = useMemo(() => {
    const blueprint = PRESET_CONFIGS[activePreset];

    if (!detectedTargets || detectedTargets.length === 0) {
      return {
        isAligned: false,
        guidanceNudge: {
          text: blueprint.defaultNudge,
          severity: "info" as const,
        },
      };
    }

    // Check proximity of detected points against active blueprint target points
    const threshold = 0.12;
    let matchedCount = 0;

    for (const target of blueprint.targetPoints) {
      const match = detectedTargets.some((d) => {
        const dx = d.x - target.x;
        const dy = d.y - target.y;
        return Math.sqrt(dx * dx + dy * dy) <= threshold;
      });
      if (match) {
        matchedCount++;
      }
    }

    const minRequiredMatches = Math.max(1, Math.min(2, blueprint.targetPoints.length));
    const aligned = matchedCount >= minRequiredMatches;

    if (aligned) {
      return {
        isAligned: true,
        guidanceNudge: {
          text: "Composition locked",
          severity: "ready" as const,
        },
      };
    }

    return {
      isAligned: false,
      guidanceNudge: {
        text: blueprint.defaultNudge,
        severity: "info" as const,
      },
    };
  }, [activePreset, detectedTargets]);

  const isAligned =
    opportunity !== null
      ? opportunity.isOptimized
      : analysisOverride !== null
      ? analysisOverride.isAligned
      : computedAlignment.isAligned;

  const guidanceNudge: GuidanceNudge =
    opportunity !== null && opportunity.actionNudge
      ? {
          text: opportunity.actionNudge.text,
          severity: opportunity.isOptimized
            ? "ready"
            : opportunity.actionNudge.priority === "high"
            ? "warning"
            : "info",
        }
      : analysisOverride !== null
      ? analysisOverride.guidanceNudge
      : computedAlignment.guidanceNudge;

  const activeArmature: ArmatureConfig = useMemo(() => {
    const blueprint = PRESET_CONFIGS[activePreset];
    return {
      id: blueprint.id,
      preset: activePreset,
      name: blueprint.name,
      targetPoints: blueprint.targetPoints,
      guideType: blueprint.guideType,
      isAligned,
    };
  }, [activePreset, isAligned]);

  const value = useMemo<CompositionContextValue>(
    () => ({
      activePreset,
      setActivePreset: (preset: GenrePreset) => {
        setOpportunity(null);
        setAnalysisOverride(null);
        setRuleResult(null);
        setActivePreset(preset);
      },
      activeArmature,
      guidanceNudge,
      isAligned,
      opportunity,
      recommendation: opportunity?.recommendation ?? null,
      illumination: opportunity?.recommendation?.illumination ?? null,
      ruleResult,
      setDetectionTargets: handleSetDetectionTargets,
      setAnalysisResult: handleSetAnalysisResult,
      setOpportunity,
      setRuleResult,
    }),
    [activePreset, activeArmature, guidanceNudge, isAligned, opportunity, ruleResult, handleSetDetectionTargets, handleSetAnalysisResult]
  );

  // Clean React 19 context provider syntax (<Context value={...}>)
  return <CompositionContext value={value}>{children}</CompositionContext>;
}

export function useComposition(): CompositionContextValue {
  const context = useContext(CompositionContext);
  if (!context) {
    throw new Error("useComposition must be used within a CompositionProvider");
  }
  return context;
}

export const useCompositionContext = useComposition;
