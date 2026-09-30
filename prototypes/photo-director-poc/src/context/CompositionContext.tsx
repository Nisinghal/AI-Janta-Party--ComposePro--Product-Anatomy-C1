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
  FLAT_LAY: {
    id: "preset-flat-lay",
    name: "Flat Lay (90°)",
    guideType: "rule_of_thirds",
    targetPoints: [
      { x: 0.5, y: 0.5, label: "center" },
    ],
    defaultNudge: "Hold parallel to table (90°)",
  },
  HERO_SHOT: {
    id: "preset-hero-shot",
    name: "Hero Shot (45°)",
    guideType: "triangle",
    targetPoints: [
      { x: 0.5, y: 0.65, label: "plate-center" },
    ],
    defaultNudge: "Lower to 45° angle",
  },
  TALL_STACK: {
    id: "preset-tall-stack",
    name: "Tall Stack (0°)",
    guideType: "rule_of_thirds",
    targetPoints: [
      { x: 0.5, y: 0.5, label: "stack-center" },
    ],
    defaultNudge: "Shoot straight on (0°)",
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
  const [activePreset, setActivePreset] = useState<GenrePreset>("FLAT_LAY");
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
