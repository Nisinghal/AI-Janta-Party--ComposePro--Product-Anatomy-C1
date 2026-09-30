"use client";

import React from "react";
import { useCompositionContext } from "../context/CompositionContext";

export default function BackgroundMergerOverlay() {
  const { opportunity } = useCompositionContext();
  const merger = opportunity?.recommendation?.mergerConflict;

  if (!merger) return null;

  const { box, label, evasionDirection } = merger;
  const isAbove = box.y > 0.12;
  const arrow = evasionDirection === "right" ? "→" : "←";

  return (
    <div
      className="background-merger-overlay"
      style={{
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        zIndex: 10,
        overflow: "hidden",
      }}
      aria-hidden="true"
    >
      <style>
        {`
          @keyframes mergerGlowPulse {
            0%, 100% {
              border-color: rgba(245, 158, 11, 0.9);
              background-color: rgba(245, 158, 11, 0.12);
              box-shadow: 0 0 10px rgba(245, 158, 11, 0.4);
            }
            50% {
              border-color: rgba(251, 191, 36, 0.6);
              background-color: rgba(245, 158, 11, 0.06);
              box-shadow: 0 0 4px rgba(245, 158, 11, 0.2);
            }
          }
          .merger-box-active {
            animation: mergerGlowPulse 1.6s ease-in-out infinite;
          }
        `}
      </style>

      {/* Conflicting Background Obstacle Highlight Box */}
      <div
        className="merger-box-active"
        style={{
          position: "absolute",
          left: `${box.x * 100}%`,
          top: `${box.y * 100}%`,
          width: `${box.width * 100}%`,
          height: `${box.height * 100}%`,
          border: "2px dashed rgba(245, 158, 11, 0.85)",
          borderRadius: "8px",
          transition: "all 0.25s ease-out",
        }}
      >
        {/* Parallax Evasion Direction Tag */}
        <div
          style={{
            position: "absolute",
            [isAbove ? "top" : "bottom"]: "-28px",
            left: "50%",
            transform: "translateX(-50%)",
            whiteSpace: "nowrap",
            backgroundColor: "rgba(15, 17, 23, 0.92)",
            border: "1px solid rgba(245, 158, 11, 0.7)",
            borderRadius: "6px",
            padding: "3px 8px",
            display: "flex",
            alignItems: "center",
            gap: "5px",
            boxShadow: "0 2px 8px rgba(0, 0, 0, 0.6)",
            fontSize: "11px",
            fontWeight: 600,
            color: "#fbbf24",
            letterSpacing: "0.02em",
          }}
        >
          <span style={{ opacity: 0.85 }}>{label}</span>
          <span style={{ color: "rgba(255, 255, 255, 0.4)" }}>•</span>
          <span style={{ color: "#fef08a" }}>
            {evasionDirection === "left" && `${arrow} `}
            Step {evasionDirection}
            {evasionDirection === "right" && ` ${arrow}`}
          </span>
        </div>
      </div>
    </div>
  );
}
