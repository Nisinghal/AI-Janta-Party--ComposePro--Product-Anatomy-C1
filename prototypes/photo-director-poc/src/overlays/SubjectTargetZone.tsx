"use client";

import React from "react";
import { useCompositionContext } from "../context/CompositionContext";
import type { BoundingBox, FramingMode, ActiveGuideline, GenrePreset } from "../types/composition";

export interface SubjectTargetZoneProps {
  targetZone?: BoundingBox | null;
  isAligned?: boolean;
  guidelineType?: ActiveGuideline | string | null;
  ready?: boolean;
  mode?: Exclude<FramingMode, "AUTO">;
}

/**
 * Shows WHERE the user should position their food — not where food currently is.
 * Each preset renders a distinct "place here" target zone:
 *   FLAT_LAY  → Large center circle with rule-of-thirds dots
 *   HERO_SHOT → Lower-third oval plate zone with angle guide
 *   TALL_STACK → Vertical center column with horizontal shelf lines
 */
export default function SubjectTargetZone({
  isAligned,
  guidelineType,
  ready,
}: SubjectTargetZoneProps) {
  const context = useCompositionContext();

  const activePreset: GenrePreset = context?.activePreset ?? "FLAT_LAY";
  const aligned: boolean =
    isAligned ?? context?.ruleResult?.isAligned ?? ready ?? context?.isAligned ?? false;

  // Colors
  const idle = "rgba(255, 255, 255, 0.3)";
  const active = "#FF6B6B";
  const fill = aligned ? "rgba(255, 107, 107, 0.06)" : "rgba(255, 255, 255, 0.03)";
  const stroke = aligned ? active : idle;
  const glow = aligned ? "0 0 24px rgba(255, 107, 107, 0.5)" : "none";

  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        zIndex: 2,
        overflow: "hidden",
      }}
      aria-hidden="true"
    >
      <style>{`
        @keyframes targetPulse {
          0%, 100% { opacity: 0.7; }
          50% { opacity: 1; }
        }
        @keyframes targetPulseAligned {
          0%, 100% { opacity: 0.85; filter: drop-shadow(0 0 6px rgba(255, 107, 107, 0.6)); }
          50% { opacity: 1; filter: drop-shadow(0 0 14px rgba(255, 107, 107, 0.9)); }
        }
        .target-label {
          position: absolute;
          left: 50%;
          transform: translateX(-50%);
          font-size: 11px;
          font-weight: 600;
          letter-spacing: 0.06em;
          color: rgba(255,255,255,0.55);
          text-shadow: 0 1px 6px rgba(0,0,0,0.5);
          white-space: nowrap;
          text-transform: uppercase;
        }
      `}</style>

      {/* ━━━━━ FLAT LAY: Large centered circle — "place your plate here" ━━━━━ */}
      {activePreset === "FLAT_LAY" && (
        <>
          {/* Main target circle */}
          <div style={{
            position: "absolute",
            left: "50%", top: "45%",
            transform: "translate(-50%, -50%)",
            width: "clamp(200px, 55vmin, 320px)",
            height: "clamp(200px, 55vmin, 320px)",
            borderRadius: "50%",
            border: `2px dashed ${stroke}`,
            backgroundColor: fill,
            boxShadow: glow,
            transition: "all 0.4s cubic-bezier(0.16, 1, 0.3, 1)",
            animation: aligned ? "targetPulseAligned 2s ease-in-out infinite" : "targetPulse 3s ease-in-out infinite",
          }}>
            {/* Crosshair ticks */}
            <div style={{ position: "absolute", left: -8, top: "50%", transform: "translateY(-50%)", width: 16, height: 1.5, background: stroke, borderRadius: 1 }} />
            <div style={{ position: "absolute", right: -8, top: "50%", transform: "translateY(-50%)", width: 16, height: 1.5, background: stroke, borderRadius: 1 }} />
            <div style={{ position: "absolute", top: -8, left: "50%", transform: "translateX(-50%)", width: 1.5, height: 16, background: stroke, borderRadius: 1 }} />
            <div style={{ position: "absolute", bottom: -8, left: "50%", transform: "translateX(-50%)", width: 1.5, height: 16, background: stroke, borderRadius: 1 }} />
            {/* Center dot */}
            <div style={{
              position: "absolute", left: "50%", top: "50%",
              transform: "translate(-50%, -50%)",
              width: aligned ? 10 : 6, height: aligned ? 10 : 6,
              borderRadius: "50%",
              background: stroke,
              boxShadow: aligned ? `0 0 8px ${active}` : "none",
              transition: "all 0.3s ease",
            }} />
          </div>
          {/* Label */}
          <span className="target-label" style={{ top: "calc(45% + clamp(110px, 29vmin, 175px))" }}>
            Place plate here · Top-down
          </span>
        </>
      )}

      {/* ━━━━━ HERO SHOT: Lower-third oval — "place dish here, shoot at 45°" ━━━━━ */}
      {activePreset === "HERO_SHOT" && (
        <>
          {/* Oval target zone in the lower third */}
          <div style={{
            position: "absolute",
            left: "50%", top: "58%",
            transform: "translate(-50%, -50%)",
            width: "clamp(220px, 62vmin, 360px)",
            height: "clamp(130px, 35vmin, 200px)",
            borderRadius: "50%",
            border: `2px dashed ${stroke}`,
            backgroundColor: fill,
            boxShadow: glow,
            transition: "all 0.4s cubic-bezier(0.16, 1, 0.3, 1)",
            animation: aligned ? "targetPulseAligned 2s ease-in-out infinite" : "targetPulse 3s ease-in-out infinite",
          }}>
            {/* Center dot */}
            <div style={{
              position: "absolute", left: "50%", top: "50%",
              transform: "translate(-50%, -50%)",
              width: aligned ? 10 : 6, height: aligned ? 10 : 6,
              borderRadius: "50%",
              background: stroke,
              boxShadow: aligned ? `0 0 8px ${active}` : "none",
              transition: "all 0.3s ease",
            }} />
          </div>
          {/* Angle guide line — subtle horizon */}
          <svg viewBox="0 0 100 100" preserveAspectRatio="none"
            style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}>
            <line x1="10" y1="42" x2="90" y2="42"
              stroke={aligned ? "rgba(255,107,107,0.3)" : "rgba(255,255,255,0.12)"}
              strokeWidth="0.5" strokeDasharray="3, 5" />
          </svg>
          {/* Label */}
          <span className="target-label" style={{ top: "calc(58% + clamp(75px, 19vmin, 115px))" }}>
            Place dish here · 45° angle
          </span>
        </>
      )}

      {/* ━━━━━ TALL STACK: Vertical center column — "stack here, shoot straight" ━━━━━ */}
      {activePreset === "TALL_STACK" && (
        <>
          {/* Vertical rectangle target */}
          <div style={{
            position: "absolute",
            left: "50%", top: "46%",
            transform: "translate(-50%, -50%)",
            width: "clamp(120px, 30vmin, 180px)",
            height: "clamp(240px, 58vmin, 380px)",
            borderRadius: "16px",
            border: `2px dashed ${stroke}`,
            backgroundColor: fill,
            boxShadow: glow,
            transition: "all 0.4s cubic-bezier(0.16, 1, 0.3, 1)",
            animation: aligned ? "targetPulseAligned 2s ease-in-out infinite" : "targetPulse 3s ease-in-out infinite",
          }}>
            {/* Horizontal shelf lines for stacking guidance */}
            <div style={{ position: "absolute", left: "10%", right: "10%", top: "33%", height: 1, background: `${stroke}`, opacity: 0.4 }} />
            <div style={{ position: "absolute", left: "10%", right: "10%", top: "66%", height: 1, background: `${stroke}`, opacity: 0.4 }} />
            {/* Center vertical axis */}
            <div style={{
              position: "absolute", left: "50%", top: "10%", bottom: "10%",
              width: 1, background: aligned ? "rgba(255,107,107,0.2)" : "rgba(255,255,255,0.1)",
              transform: "translateX(-50%)",
            }} />
            {/* Center dot */}
            <div style={{
              position: "absolute", left: "50%", top: "50%",
              transform: "translate(-50%, -50%)",
              width: aligned ? 10 : 6, height: aligned ? 10 : 6,
              borderRadius: "50%",
              background: stroke,
              boxShadow: aligned ? `0 0 8px ${active}` : "none",
              transition: "all 0.3s ease",
            }} />
          </div>
          {/* Label */}
          <span className="target-label" style={{ top: "calc(46% + clamp(130px, 31vmin, 205px))" }}>
            Stack here · Eye-level
          </span>
        </>
      )}
    </div>
  );
}
