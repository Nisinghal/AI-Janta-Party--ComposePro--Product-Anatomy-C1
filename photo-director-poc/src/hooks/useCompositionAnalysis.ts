"use client";

import { useCallback, useRef, useState } from "react";
import { useCompositionContext } from "../context/CompositionContext";
import {
  evaluatePhotographicDirecting,
  type DirectingRecommendation,
} from "../engine/photographicDirectingEngine";
import {
  evaluateSceneComposition,
  type CompositionOpportunity,
} from "../engine/sceneEvaluator";
import type { GuidanceNudge } from "../types/composition";

import { assessFaceIllumination } from "../vision/faceIlluminationEngine";

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

export interface RawPoseLandmark {
  x: number;
  y: number;
  z?: number;
  visibility?: number;
}

export interface AnalysisInput {
  faces?: any[];
  poses?: any[];
  objects?: any[];
  poseLandmarks?: RawPoseLandmark[];
  faceMeshLandmarks?: Array<{ x: number; y: number; z?: number }> | null;
  videoWidth: number;
  videoHeight: number;
  mirrored?: boolean;
  zoomRatio?: number;
  videoElement?: HTMLVideoElement | HTMLCanvasElement | null;
}

export function useCompositionAnalysis() {
  const { setAnalysisResult, setOpportunity, setDetectionTargets } = useCompositionContext();

  // Local state holding the current active opportunity from SceneEvaluator
  const [currentOpportunity, setCurrentOpportunity] = useState<CompositionOpportunity | null>(null);

  // Timestamp ref to throttle evaluation to ~300ms
  const lastEvalTimeRef = useRef<number>(0);

  // Timestamp ref for 350ms debounce before switching into isOptimized = true
  const optimizedSinceRef = useRef<number | null>(null);

  const analyzeFrame = useCallback(
    ({
      faces = [],
      poses = [],
      objects = [],
      poseLandmarks,
      faceMeshLandmarks,
      videoWidth,
      videoHeight,
      mirrored = false,
      zoomRatio = 1.0,
      videoElement = null,
    }: AnalysisInput): CompositionOpportunity | null => {
      const now = performance.now();

      // Throttle evaluations to ~300ms using timestamp ref
      if (now - lastEvalTimeRef.current < 300 && lastEvalTimeRef.current !== 0) {
        return currentOpportunity;
      }
      lastEvalTimeRef.current = now;

      // Normalise pose inputs
      const activePoses = poses.length > 0 ? poses : poseLandmarks ? [poseLandmarks] : [];

      // Forward live MediaPipe detections directly into SceneEvaluator
      const rawOpportunity = evaluateSceneComposition(
        faces,
        activePoses,
        objects,
        videoWidth,
        videoHeight
      );

      // Extract primary face and primary pose landmarks for Photographic Directing Engine
      const primaryFace = Array.isArray(faces) && faces.length > 0 ? faces[0] : null;
      const primaryPose = activePoses.length > 0 ? activePoses[0] : null;
      const landmarks = Array.isArray(primaryPose)
        ? primaryPose
        : primaryPose?.landmarks ?? null;

      // Assess real-time facial illumination (split shadows, backlighting, flat light)
      const primaryFaceBox = rawOpportunity.primarySubjectBox ?? (primaryFace?.boundingBox ? {
        x: (primaryFace.boundingBox.originX ?? 0) / (videoWidth || 1),
        y: (primaryFace.boundingBox.originY ?? 0) / (videoHeight || 1),
        width: (primaryFace.boundingBox.width ?? 0) / (videoWidth || 1),
        height: (primaryFace.boundingBox.height ?? 0) / (videoHeight || 1),
      } : null);

      const illumination = videoElement && primaryFaceBox
        ? assessFaceIllumination(videoElement, primaryFaceBox, mirrored, faceMeshLandmarks)
        : null;

      // Evaluate Level 1 (Fatal Crops), Level 1.5 (Background Mergers), Level 1.8 (Illumination), Level 2 (Lens/Distance), Level 3 (Lock/Quiet)
      const recommendation: DirectingRecommendation = evaluatePhotographicDirecting(
        landmarks,
        primaryFaceBox,
        { zoomRatio, frameWidth: videoWidth, frameHeight: videoHeight },
        objects,
        illumination
      );

      // If Level 1 or Level 2 flaws are detected, they override raw optimization
      const isEligibleForLock =
        recommendation.priority === "locked" ||
        (recommendation.priority === "coaching" &&
          recommendation.pillText === null &&
          rawOpportunity.isOptimized);

      // 350ms debounce before switching into isOptimized = true to prevent flicker
      let debouncedIsOptimized = false;
      if (isEligibleForLock) {
        if (optimizedSinceRef.current === null) {
          optimizedSinceRef.current = now;
        }
        if (now - optimizedSinceRef.current >= 350) {
          debouncedIsOptimized = true;
        }
      } else {
        optimizedSinceRef.current = null;
      }

      // Determine active nudge text based on priority hierarchy
      let finalNudgeText: string | null = null;
      let finalPriority: 'low' | 'medium' | 'high' = 'low';

      if (recommendation.priority === "critical") {
        finalNudgeText = recommendation.pillText;
        finalPriority = "high";
      } else if (recommendation.priority === "warning") {
        finalNudgeText = recommendation.pillText;
        finalPriority = "high";
      } else if (recommendation.priority === "coaching" && recommendation.pillText) {
        finalNudgeText = recommendation.pillText;
        finalPriority = "medium";
      } else if (debouncedIsOptimized) {
        finalNudgeText = recommendation.pillText || "Composition locked";
        finalPriority = "high";
      } else if (isEligibleForLock) {
        finalNudgeText = "Hold steady";
        finalPriority = "high";
      } else {
        finalNudgeText = rawOpportunity.actionNudge?.text ?? null;
        finalPriority = rawOpportunity.actionNudge?.priority ?? "low";
      }

      const opportunity: CompositionOpportunity = {
        ...rawOpportunity,
        isOptimized: debouncedIsOptimized,
        recommendation: {
          ...recommendation,
          isOptimized: debouncedIsOptimized,
          pillText: finalNudgeText,
        },
        actionNudge: finalNudgeText
          ? { text: finalNudgeText, priority: finalPriority }
          : null,
      };

      // Update local state
      setCurrentOpportunity(opportunity);

      // Propagate opportunity to CompositionContext
      setOpportunity(opportunity);

      // If tableware/food objects are present, register centroids for target alignment
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

      // Propagate analysis result and action nudge to context
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
    [currentOpportunity, setAnalysisResult, setDetectionTargets, setOpportunity]
  );

  return {
    analyzeFrame,
    currentOpportunity,
    opportunity: currentOpportunity,
    targetAnchor: currentOpportunity?.targetAnchor ?? null,
    targetZone: currentOpportunity?.targetAnchor ?? null,
    recommendation: currentOpportunity?.recommendation ?? null,
  };
}
