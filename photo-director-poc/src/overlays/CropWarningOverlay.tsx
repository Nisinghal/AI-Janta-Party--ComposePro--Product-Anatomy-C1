"use client";

import React from "react";
import { useCompositionContext } from "../context/CompositionContext";

export default function CropWarningOverlay() {
  const { opportunity } = useCompositionContext();
  const recommendation = opportunity?.recommendation;
  const edge = recommendation?.highlightEdge;

  if (!edge) return null;

  return (
    <div
      className="crop-warning-overlay"
      style={{
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        zIndex: 12,
        overflow: "hidden",
      }}
      aria-hidden="true"
    >
      <style>
        {`
          @keyframes cropBarPulse {
            0%, 100% {
              opacity: 0.95;
              filter: drop-shadow(0 0 6px rgba(239, 68, 68, 0.8));
            }
            50% {
              opacity: 0.55;
              filter: drop-shadow(0 0 2px rgba(245, 158, 11, 0.5));
            }
          }
          .crop-bar-active {
            animation: cropBarPulse 1.2s ease-in-out infinite;
          }
        `}
      </style>

      {/* Top Edge Warning */}
      {edge === "top" && (
        <div
          className="crop-bar-active"
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            right: 0,
            height: "4px",
            background:
              "repeating-linear-gradient(90deg, #ef4444, #ef4444 8px, #f59e0b 8px, #f59e0b 16px)",
            boxShadow:
              "0 0 10px rgba(239, 68, 68, 0.8), 0 2px 6px rgba(245, 158, 11, 0.6)",
          }}
        />
      )}

      {/* Bottom Edge Warning */}
      {edge === "bottom" && (
        <div
          className="crop-bar-active"
          style={{
            position: "absolute",
            bottom: 0,
            left: 0,
            right: 0,
            height: "4px",
            background:
              "repeating-linear-gradient(90deg, #ef4444, #ef4444 8px, #f59e0b 8px, #f59e0b 16px)",
            boxShadow:
              "0 0 10px rgba(239, 68, 68, 0.8), 0 -2px 6px rgba(245, 158, 11, 0.6)",
          }}
        />
      )}

      {/* Left Edge Warning */}
      {edge === "left" && (
        <div
          className="crop-bar-active"
          style={{
            position: "absolute",
            top: 0,
            bottom: 0,
            left: 0,
            width: "4px",
            background:
              "repeating-linear-gradient(180deg, #ef4444, #ef4444 8px, #f59e0b 8px, #f59e0b 16px)",
            boxShadow:
              "0 0 10px rgba(239, 68, 68, 0.8), 2px 0 6px rgba(245, 158, 11, 0.6)",
          }}
        />
      )}

      {/* Right Edge Warning */}
      {edge === "right" && (
        <div
          className="crop-bar-active"
          style={{
            position: "absolute",
            top: 0,
            bottom: 0,
            right: 0,
            width: "4px",
            background:
              "repeating-linear-gradient(180deg, #ef4444, #ef4444 8px, #f59e0b 8px, #f59e0b 16px)",
            boxShadow:
              "0 0 10px rgba(239, 68, 68, 0.8), -2px 0 6px rgba(245, 158, 11, 0.6)",
          }}
        />
      )}
    </div>
  );
}
