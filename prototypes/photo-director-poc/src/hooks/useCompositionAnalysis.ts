"use client";

import { useCallback, useRef, useState, useEffect } from "react";
import { useCompositionContext } from "../context/CompositionContext";
import {
  evaluateSceneComposition,
  type CompositionOpportunity,
} from "../engine/sceneEvaluator";
import type { GuidanceNudge } from "../types/composition";

export interface DetectionBox {
  originX: number;
  originY: number;
  width: number;
  height: number;
}

export interface RawObjectDetection {
  boundingBox?: DetectionBox;
  categories?: { categoryName?: string; score?: number }[];
}

export interface AnalysisInput {
  objects?: any[];
  videoWidth: number;
  videoHeight: number;
  mirrored?: boolean;
  zoomRatio?: number;
  videoElement?: HTMLVideoElement | HTMLCanvasElement | null;
}

export function useCompositionAnalysis() {
  const { setAnalysisResult, setOpportunity, setDetectionTargets } = useCompositionContext();

  const [currentOpportunity, setCurrentOpportunity] = useState<CompositionOpportunity | null>(null);
  const lastEvalTimeRef = useRef<number>(0);
  const optimizedSinceRef = useRef<number | null>(null);
  
  // Track device angle
  const [devicePitch, setDevicePitch] = useState<number>(0);

  useEffect(() => {
    const handleOrientation = (event: DeviceOrientationEvent) => {
      if (event.beta !== null) {
        setDevicePitch(event.beta);
      }
    };
    window.addEventListener("deviceorientation", handleOrientation);
    return () => window.removeEventListener("deviceorientation", handleOrientation);
  }, []);

  const analyzeFrame = useCallback(
    ({
      objects = [],
      videoWidth,
      videoHeight,
      mirrored = false,
      zoomRatio = 1.0,
      videoElement = null,
    }: AnalysisInput): CompositionOpportunity | null => {
      const now = performance.now();

      if (now - lastEvalTimeRef.current < 300 && lastEvalTimeRef.current !== 0) {
        return currentOpportunity;
      }
      lastEvalTimeRef.current = now;

      // Always pass empty arrays for faces and poses
      const rawOpportunity = evaluateSceneComposition(
        [],
        [],
        objects,
        videoWidth,
        videoHeight,
        devicePitch
      );

      let debouncedIsOptimized = false;
      if (rawOpportunity.isOptimized) {
        if (optimizedSinceRef.current === null) {
          optimizedSinceRef.current = now;
        }
        if (now - optimizedSinceRef.current >= 350) {
          debouncedIsOptimized = true;
        }
      } else {
        optimizedSinceRef.current = null;
      }

      const finalNudgeText = rawOpportunity.actionNudge?.text ?? null;
      const finalPriority = rawOpportunity.actionNudge?.priority ?? "low";

      const opportunity: CompositionOpportunity = {
        ...rawOpportunity,
        isOptimized: debouncedIsOptimized,
        actionNudge: finalNudgeText
          ? { text: finalNudgeText, priority: finalPriority }
          : null,
      };

      setCurrentOpportunity(opportunity);
      setOpportunity(opportunity);

      if (opportunity.scene === "tabletop_food" && objects.length > 0) {
        const centroids = objects
          .filter((d: any) => d.boundingBox)
          .map((d: any) => {
            const b = d.boundingBox;
            const cx = (b.originX + b.width / 2) / videoWidth;
            const cy = (b.originY + b.height / 2) / videoHeight;
            return {
              x: mirrored ? 1 - cx : cx,
              y: cy,
              label: d.categories?.[0]?.categoryName,
            };
          });
        setDetectionTargets(centroids);
      }

      if (finalNudgeText) {
        const nudge: GuidanceNudge = {
          text: finalNudgeText,
          severity: debouncedIsOptimized
            ? "ready"
            : finalPriority === "high"
            ? "warning"
            : "info",
        };
        setAnalysisResult(debouncedIsOptimized, nudge);
      }

      return opportunity;
    },
    [currentOpportunity, setAnalysisResult, setDetectionTargets, setOpportunity, devicePitch]
  );

  return {
    analyzeFrame,
    currentOpportunity,
    opportunity: currentOpportunity,
    targetAnchor: currentOpportunity?.targetAnchor ?? null,
    targetZone: currentOpportunity?.targetAnchor ?? null,
    recommendation: currentOpportunity?.recommendation ?? null,
    devicePitch,
  };
}

