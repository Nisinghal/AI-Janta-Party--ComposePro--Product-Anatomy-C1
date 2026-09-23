"use client";

import React from "react";
import { useCompositionContext } from "../context/CompositionContext";
import type { BoundingBox, FramingMode, ActiveGuideline, GenrePreset } from "../types/composition";

export interface SubjectTargetZoneProps {
  /** Target bounding box in normalized 0.0 to 1.0 coordinates */
  targetZone?: BoundingBox | null;
  /** Whether the composition is aligned to trigger the #d5ff48 glow state */
  isAligned?: boolean;
  /** Active visual guideline overlay type */
  guidelineType?: ActiveGuideline | string | null;
  /** Legacy prop compatibility */
  ready?: boolean;
  mode?: Exclude<FramingMode, "AUTO">;
}

/**
 * SubjectTargetZone provides clean, professional photographic guidelines
 * tailored to each genre preset (Rule of Thirds, Horizon, Overhead Reticle, Keystone),
 * completely free of distracting heavy corner boxes in the middle of the subject's face.
 */
export default function SubjectTargetZone({
  isAligned,
  guidelineType,
  ready,
}: SubjectTargetZoneProps) {
  const context = useCompositionContext();

  const activePreset: GenrePreset = context?.activePreset ?? "PEOPLE";
  const effectiveAligned: boolean =
    isAligned ?? context?.ruleResult?.isAligned ?? ready ?? context?.isAligned ?? false;

  const effectiveGuideline: ActiveGuideline | string | undefined =
    guidelineType ?? context?.ruleResult?.activeGuideline;

  const strokeColor = effectiveAligned ? "#d5ff48" : "rgba(255, 255, 255, 0.22)";
  const activeGlow = effectiveAligned ? "drop-shadow(0 0 8px rgba(213, 255, 72, 0.8))" : "none";

  return (
    <div
      className={`subject-target-zone-clean ${effectiveAligned ? "is-aligned" : ""}`}
      style={{
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        zIndex: 2,
        overflow: "hidden",
      }}
      aria-hidden="true"
    >
      <style>
        {`
          @keyframes guideGlowPulse {
            0%, 100% { opacity: 0.85; filter: drop-shadow(0 0 5px rgba(213, 255, 72, 0.7)); }
            50% { opacity: 1; filter: drop-shadow(0 0 12px rgba(213, 255, 72, 0.95)); }
          }
          .guide-aligned {
            animation: guideGlowPulse 1.8s ease-in-out infinite;
          }
        `}
      </style>

      {/* 1. PEOPLE PRESET: Faint, non-distracting Rule of Thirds grid with eye-level anchor */}
      {activePreset === "PEOPLE" && (
        <svg
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        >
          {/* Subtle Vertical Thirds */}
          <line x1="38" y1="5" x2="38" y2="95" stroke={strokeColor} strokeWidth={effectiveAligned ? "0.8" : "0.5"} strokeDasharray="3, 5" strokeOpacity={effectiveAligned ? 0.9 : 0.25} />
          <line x1="62" y1="5" x2="62" y2="95" stroke="rgba(255, 255, 255, 0.12)" strokeWidth="0.4" strokeDasharray="3, 5" />

          {/* Optimal Eye-Level Balance Line (y = 34%) */}
          <line
            x1="5"
            y1="34"
            x2="95"
            y2="34"
            stroke={strokeColor}
            strokeWidth={effectiveAligned ? "1.2" : "0.6"}
            strokeDasharray="4, 4"
            strokeOpacity={effectiveAligned ? 1 : 0.4}
            style={{ filter: activeGlow, transition: "stroke 0.3s ease" }}
          />

          {/* Golden intersection target at x=0.38, y=0.34 */}
          <circle
            cx="38"
            cy="34"
            r={effectiveAligned ? "2.5" : "1.8"}
            fill={effectiveAligned ? "#d5ff48" : "none"}
            stroke={strokeColor}
            strokeWidth="0.8"
            strokeOpacity={effectiveAligned ? 1 : 0.6}
            style={{ filter: activeGlow }}
          />
        </svg>
      )}

      {/* 2. FOOD PRESET - OVERHEAD FLATLAY: Circular dashed leveling reticle at center */}
      {activePreset === "FOOD" && effectiveGuideline === "OVERHEAD_CROSSHAIR" && (
        <div
          style={{
            position: "absolute",
            left: "50%",
            top: "50%",
            transform: "translate(-50%, -50%)",
            width: "clamp(100px, 28vw, 140px)",
            height: "clamp(100px, 28vw, 140px)",
          }}
        >
          {/* Circular dashed leveling ring */}
          <div
            style={{
              position: "absolute",
              inset: 0,
              borderRadius: "50%",
              border: `2px dashed ${effectiveAligned ? "#d5ff48" : "rgba(255, 255, 255, 0.5)"}`,
              boxShadow: effectiveAligned ? "0 0 20px rgba(213, 255, 72, 0.75)" : "none",
              transition: "all 0.3s cubic-bezier(0.16, 1, 0.3, 1)",
            }}
          />

          {/* Crosshair horizontal/vertical markers */}
          <div style={{ position: "absolute", left: 0, top: "50%", transform: "translateY(-50%)", width: "16px", height: "2px", backgroundColor: strokeColor }} />
          <div style={{ position: "absolute", right: 0, top: "50%", transform: "translateY(-50%)", width: "16px", height: "2px", backgroundColor: strokeColor }} />
          <div style={{ position: "absolute", top: 0, left: "50%", transform: "translateX(-50%)", width: "2px", height: "16px", backgroundColor: strokeColor }} />
          <div style={{ position: "absolute", bottom: 0, left: "50%", transform: "translateX(-50%)", width: "2px", height: "16px", backgroundColor: strokeColor }} />

          {/* Center bullseye dot */}
          <div
            style={{
              position: "absolute",
              left: "50%",
              top: "50%",
              transform: "translate(-50%, -50%)",
              width: effectiveAligned ? "10px" : "6px",
              height: effectiveAligned ? "10px" : "6px",
              borderRadius: "50%",
              backgroundColor: effectiveAligned ? "#d5ff48" : "rgba(255, 255, 255, 0.6)",
              boxShadow: effectiveAligned ? "0 0 10px #d5ff48" : "none",
              transition: "all 0.3s ease",
            }}
          />
        </div>
      )}

      {/* 2b. FOOD PRESET - 45 DEGREE PERSPECTIVE: Lower-third hero dish guide */}
      {activePreset === "FOOD" && effectiveGuideline !== "OVERHEAD_CROSSHAIR" && (
        <svg
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        >
          {/* Lower third dish baseline y=65% */}
          <line
            x1="15"
            y1="65"
            x2="85"
            y2="65"
            stroke={strokeColor}
            strokeWidth={effectiveAligned ? "1.4" : "0.7"}
            strokeDasharray="4, 4"
            style={{ filter: activeGlow }}
          />
          {/* Subtle dish framing arc */}
          <path
            d="M 28 65 C 28 78, 72 78, 72 65"
            fill="none"
            stroke={strokeColor}
            strokeWidth="0.8"
            strokeDasharray="3, 3"
            strokeOpacity={effectiveAligned ? 0.9 : 0.35}
            style={{ filter: activeGlow }}
          />
        </svg>
      )}

      {/* 3. NATURE PRESET: Clean Horizon line at y=67% */}
      {activePreset === "NATURE" && (
        <svg
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        >
          <line
            x1="0"
            y1="67"
            x2="100"
            y2="67"
            stroke={strokeColor}
            strokeWidth={effectiveAligned ? "1.5" : "0.8"}
            strokeDasharray={effectiveAligned ? "none" : "5, 4"}
            style={{ filter: activeGlow, transition: "stroke 0.3s ease" }}
          />
          {/* Vertical third guide lines */}
          <line x1="33" y1="20" x2="33" y2="85" stroke="rgba(255, 255, 255, 0.15)" strokeWidth="0.5" strokeDasharray="3, 4" />
          <line x1="67" y1="20" x2="67" y2="85" stroke="rgba(255, 255, 255, 0.15)" strokeWidth="0.5" strokeDasharray="3, 4" />
        </svg>
      )}

      {/* 4. STREET PRESET: Clean vertical keystone & vanishing guides */}
      {activePreset === "STREET" && (
        <svg
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        >
          {/* Left vertical keystone guide */}
          <line x1="25" y1="5" x2="25" y2="95" stroke={strokeColor} strokeWidth={effectiveAligned ? "1.2" : "0.6"} strokeDasharray="4, 4" style={{ filter: activeGlow }} />
          {/* Right vertical keystone guide */}
          <line x1="75" y1="5" x2="75" y2="95" stroke={strokeColor} strokeWidth={effectiveAligned ? "1.2" : "0.6"} strokeDasharray="4, 4" style={{ filter: activeGlow }} />
          {/* Vanishing line center */}
          <line x1="50" y1="20" x2="50" y2="85" stroke="rgba(255, 255, 255, 0.18)" strokeWidth="0.5" strokeDasharray="2, 4" />
        </svg>
      )}

      {/* 5. MACRO PRESET: Subtle center detail focus zone */}
      {activePreset === "MACRO" && (
        <div
          style={{
            position: "absolute",
            left: "50%",
            top: "50%",
            transform: "translate(-50%, -50%)",
            width: "clamp(80px, 22vw, 110px)",
            height: "clamp(80px, 22vw, 110px)",
            borderRadius: "50%",
            border: `1.5px dashed ${strokeColor}`,
            boxShadow: effectiveAligned ? "0 0 16px rgba(213, 255, 72, 0.6)" : "none",
            transition: "all 0.3s ease",
          }}
        />
      )}
    </div>
  );
}
