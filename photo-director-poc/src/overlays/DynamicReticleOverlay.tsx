"use client";

import React, { useEffect, useRef } from "react";
import { useCompositionContext } from "../context/CompositionContext";

/**
 * DynamicReticleOverlay handles edge-crop boundary warnings.
 * Dummy placeholder silhouettes and redundant brackets have been removed
 * to keep the viewfinder clean and focused.
 */
export default function DynamicReticleOverlay() {
  const { opportunity, isAligned: contextAligned } = useCompositionContext();

  const edgeViolation = opportunity?.edgeViolation;
  const isOptimized = opportunity?.isOptimized ?? contextAligned ?? false;

  const hasEdgeViolation = edgeViolation?.hasViolation ?? false;
  const edges = edgeViolation?.edges ?? [];

  // Ref to track optimization transition for subtle haptic buzz
  const prevOptimizedRef = useRef(false);

  useEffect(() => {
    if (isOptimized && !prevOptimizedRef.current) {
      if (typeof navigator !== "undefined" && typeof navigator.vibrate === "function") {
        try {
          navigator.vibrate([30, 20, 40]);
        } catch {
          // Ignore vibration limitations
        }
      }
    }
    prevOptimizedRef.current = isOptimized;
  }, [isOptimized]);

  if (!hasEdgeViolation) {
    return null;
  }

  return (
    <div
      className="dynamic-reticle-overlay"
      style={{
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        zIndex: 4,
        overflow: "hidden",
      }}
      aria-hidden="true"
    >
      {/* Perimeter Warning Borders (Amber/Red Vignette along offending edges) */}
      {edges.includes("top") && (
        <div
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            right: 0,
            height: "12px",
            background:
              "linear-gradient(to bottom, rgba(239, 68, 68, 0.7) 0%, rgba(245, 110, 30, 0.4) 60%, transparent 100%)",
            pointerEvents: "none",
            zIndex: 10,
          }}
        />
      )}

      {edges.includes("bottom") && (
        <div
          style={{
            position: "absolute",
            bottom: 0,
            left: 0,
            right: 0,
            height: "12px",
            background:
              "linear-gradient(to top, rgba(239, 68, 68, 0.7) 0%, rgba(245, 110, 30, 0.4) 60%, transparent 100%)",
            pointerEvents: "none",
            zIndex: 10,
          }}
        />
      )}

      {edges.includes("left") && (
        <div
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            bottom: 0,
            width: "12px",
            background:
              "linear-gradient(to right, rgba(239, 68, 68, 0.7) 0%, rgba(245, 110, 30, 0.4) 60%, transparent 100%)",
            pointerEvents: "none",
            zIndex: 10,
          }}
        />
      )}

      {edges.includes("right") && (
        <div
          style={{
            position: "absolute",
            top: 0,
            right: 0,
            bottom: 0,
            width: "12px",
            background:
              "linear-gradient(to left, rgba(239, 68, 68, 0.7) 0%, rgba(245, 110, 30, 0.4) 60%, transparent 100%)",
            pointerEvents: "none",
            zIndex: 10,
          }}
        />
      )}
    </div>
  );
}
