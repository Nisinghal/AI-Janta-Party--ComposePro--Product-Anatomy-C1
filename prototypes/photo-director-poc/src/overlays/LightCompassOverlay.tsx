"use client";

import React from "react";
import { useCompositionContext } from "../context/CompositionContext";
import { Sun, ArrowRight, ArrowLeft } from "lucide-react";

export default function LightCompassOverlay() {
  const { illumination, opportunity } = useCompositionContext();

  // Only render when a subject/illumination assessment is present
  if (!illumination || opportunity?.scene !== "portrait") {
    return null;
  }

  const { quality, angleDegrees, turnDirection, contrastRatio } = illumination;

  const isHarsh = quality === "harsh_shadow";
  const isBacklit = quality === "backlit";
  const isBalanced = quality === "balanced";

  // Visual styling based on light state
  let accentColor = "rgba(255, 255, 255, 0.5)";
  let ringColor = "rgba(255, 255, 255, 0.12)";
  let statusText = "Flat light";

  if (isBalanced) {
    accentColor = "#d5ff48";
    ringColor = "rgba(213, 255, 72, 0.35)";
    statusText = "Balanced light";
  } else if (isHarsh) {
    accentColor = "#f59e0b";
    ringColor = "rgba(245, 158, 11, 0.5)";
    statusText = "Harsh shadow";
  } else if (isBacklit) {
    accentColor = "#ef4444";
    ringColor = "rgba(239, 68, 68, 0.5)";
    statusText = "Backlit";
  }

  return (
    <aside
      className="visual-light-compass"
      aria-label={`Visual light compass: ${statusText}`}
      style={{
        position: "absolute",
        top: "76px",
        right: "14px",
        zIndex: 10,
        pointerEvents: "none",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: "4px",
        userSelect: "none",
      }}
    >
      <style>
        {`
          @keyframes compassPulse {
            0%, 100% {
              box-shadow: 0 0 10px rgba(245, 158, 11, 0.3), inset 0 0 6px rgba(245, 158, 11, 0.2);
            }
            50% {
              box-shadow: 0 0 18px rgba(245, 158, 11, 0.6), inset 0 0 12px rgba(245, 158, 11, 0.4);
            }
          }
          .compass-warning-pulse {
            animation: compassPulse 1.6s ease-in-out infinite;
          }
        `}
      </style>

      {/* Circular Compass Dial */}
      <div
        className={isHarsh || isBacklit ? "compass-warning-pulse" : ""}
        style={{
          position: "relative",
          width: "46px",
          height: "46px",
          borderRadius: "50%",
          backgroundColor: "rgba(14, 16, 22, 0.82)",
          backdropFilter: "blur(14px)",
          WebkitBackdropFilter: "blur(14px)",
          border: `1.5px solid ${ringColor}`,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          transition: "all 0.35s ease-out",
        }}
      >
        {/* Subtle Cardinal Crosshairs */}
        <div
          style={{
            position: "absolute",
            width: "34px",
            height: "1px",
            backgroundColor: "rgba(255, 255, 255, 0.08)",
          }}
        />
        <div
          style={{
            position: "absolute",
            width: "1px",
            height: "34px",
            backgroundColor: "rgba(255, 255, 255, 0.08)",
          }}
        />

        {/* Center Face/Subject Anchor */}
        <div
          style={{
            width: "10px",
            height: "10px",
            borderRadius: "50%",
            backgroundColor: isBalanced ? "rgba(213, 255, 72, 0.3)" : "rgba(255, 255, 255, 0.2)",
            border: `1px solid ${accentColor}`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <div
            style={{
              width: "3px",
              height: "3px",
              borderRadius: "50%",
              backgroundColor: accentColor,
            }}
          />
        </div>

        {/* Rotating Key Light Pointer & Pip */}
        <div
          style={{
            position: "absolute",
            inset: 0,
            transform: `rotate(${angleDegrees}deg)`,
            transition: "transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)",
            display: "flex",
            justifyContent: "center",
          }}
        >
          {/* Light Pip at perimeter */}
          <div
            style={{
              width: "8px",
              height: "8px",
              borderRadius: "50%",
              marginTop: "2px",
              backgroundColor: accentColor,
              boxShadow: `0 0 8px ${accentColor}`,
              border: "1.5px solid #0e1016",
            }}
          />
        </div>

        {/* Turn Direction Arrow Inside Compass when split shadow is detected */}
        {isHarsh && turnDirection && (
          <div
            style={{
              position: "absolute",
              bottom: "-6px",
              backgroundColor: "rgba(245, 158, 11, 0.95)",
              borderRadius: "50%",
              width: "14px",
              height: "14px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#000",
              boxShadow: "0 2px 6px rgba(0,0,0,0.5)",
            }}
          >
            {turnDirection === "left" ? <ArrowLeft size={10} strokeWidth={3} /> : <ArrowRight size={10} strokeWidth={3} />}
          </div>
        )}
      </div>

      {/* Mini Status Tag */}
      <span
        style={{
          fontSize: "9px",
          fontWeight: 600,
          letterSpacing: "0.03em",
          color: accentColor,
          backgroundColor: "rgba(14, 16, 22, 0.8)",
          padding: "1px 6px",
          borderRadius: "4px",
          border: `1px solid ${ringColor}`,
          whiteSpace: "nowrap",
          backdropFilter: "blur(8px)",
        }}
      >
        {statusText}
      </span>
    </aside>
  );
}
