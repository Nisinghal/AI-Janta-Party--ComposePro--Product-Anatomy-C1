"use client";

import React from "react";
import { useCompositionContext } from "../context/CompositionContext";

export default function SeatedPoseArmature() {
  const { isAligned } = useCompositionContext();

  const strokeColor = isAligned ? "#d5ff48" : "rgba(255, 255, 255, 0.2)";
  const activeGlow = isAligned ? "drop-shadow(0 0 6px #d5ff48)" : "none";
  const wireframeStroke = isAligned ? "rgba(213, 255, 72, 0.45)" : "rgba(255, 255, 255, 0.2)";
  const wireframeFill = isAligned ? "rgba(213, 255, 72, 0.07)" : "rgba(255, 255, 255, 0.02)";

  return (
    <svg
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      className={`seated-pose-armature ${isAligned ? "armature-aligned" : ""}`}
      style={{
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        pointerEvents: "none",
        zIndex: 2,
        overflow: "visible",
      }}
      aria-hidden="true"
    >
      <style>
        {`
          @keyframes armaturePulse {
            0%, 100% {
              opacity: 0.95;
              filter: drop-shadow(0 0 5px rgba(213, 255, 72, 0.8));
            }
            50% {
              opacity: 0.65;
              filter: drop-shadow(0 0 10px rgba(213, 255, 72, 0.95));
            }
          }
          .armature-aligned {
            animation: armaturePulse 1.8s ease-in-out infinite;
          }
        `}
      </style>

      {/* 1. Optimal Eye-Line Balance Line (y: 35%) */}
      <g>
        <line
          x1={2}
          y1={35}
          x2={98}
          y2={35}
          stroke={strokeColor}
          strokeWidth={isAligned ? 1.2 : 0.8}
          strokeDasharray="3, 4"
          style={{
            transition: "stroke 0.3s ease, stroke-width 0.3s ease",
            filter: activeGlow,
          }}
        />
        {/* Subtle eye-line marker label */}
        <text
          x={4}
          y={33.5}
          fill={strokeColor}
          fontSize="2.4"
          fontWeight="600"
          letterSpacing="0.08em"
          style={{ transition: "fill 0.3s ease", userSelect: "none" }}
        >
          EYE LEVEL · 35%
        </text>
      </g>

      {/* 2. Dynamic Diagonal Guideline (25%, 80%) to (75%, 35%) */}
      <g>
        <line
          x1={25}
          y1={80}
          x2={75}
          y2={35}
          stroke={strokeColor}
          strokeWidth={isAligned ? 1.6 : 1.0}
          strokeDasharray="4, 4"
          style={{
            transition: "stroke 0.3s ease, stroke-width 0.3s ease",
            filter: activeGlow,
          }}
        />

        {/* Diagonal anchor endpoints */}
        <circle
          cx={75}
          cy={35}
          r={isAligned ? 2.8 : 2.2}
          fill={isAligned ? "rgba(213, 255, 72, 0.25)" : "rgba(255, 255, 255, 0.05)"}
          stroke={strokeColor}
          strokeWidth={isAligned ? 1.4 : 0.8}
        />
        <circle
          cx={25}
          cy={80}
          r={isAligned ? 2.8 : 2.2}
          fill={isAligned ? "rgba(213, 255, 72, 0.25)" : "rgba(255, 255, 255, 0.05)"}
          stroke={strokeColor}
          strokeWidth={isAligned ? 1.4 : 0.8}
        />
      </g>

      {/* 3. Faint Head-and-Torso Minimalist Silhouette Wireframe aligned to diagonal */}
      <g style={{ transition: "all 0.3s ease" }}>
        {/* Minimalist Head Wireframe (positioned over eye-line and upper diagonal) */}
        <ellipse
          cx={73}
          cy={27}
          rx={5.5}
          ry={7.0}
          transform="rotate(-8 73 27)"
          fill={wireframeFill}
          stroke={wireframeStroke}
          strokeWidth={isAligned ? 1.2 : 0.8}
          strokeDasharray="3, 3"
        />

        {/* Head Center Eye Target Line */}
        <line
          x1={69}
          y1={29}
          x2={77}
          y2={29}
          stroke={wireframeStroke}
          strokeWidth={0.7}
          strokeDasharray="2, 2"
        />

        {/* Minimalist Neck and Shoulder Wireframe */}
        <path
          d="M 69.5 33.5 L 68 37.5 L 56 42.5 M 76.5 33.5 L 78 37.5 L 87 43.5"
          fill="none"
          stroke={wireframeStroke}
          strokeWidth={isAligned ? 1.2 : 0.8}
          strokeDasharray="3, 3"
          strokeLinejoin="round"
        />

        {/* Torso & Seated Angle Wireframe (sloping along the 35-45 deg diagonal) */}
        <path
          d="
            M 56 42.5
            C 53 52, 48 62, 38 72
            L 25 80
            M 87 43.5
            C 82 55, 74 66, 58 76
            L 34 84
          "
          fill="none"
          stroke={wireframeStroke}
          strokeWidth={isAligned ? 1.2 : 0.8}
          strokeDasharray="4, 4"
          strokeLinejoin="round"
        />

        {/* Subtle Spine/Torso Alignment Axis */}
        <path
          d="M 73 34 C 67 48, 56 62, 32 80"
          fill="none"
          stroke={strokeColor}
          strokeWidth={0.7}
          strokeDasharray="2, 3"
          strokeOpacity={isAligned ? 0.7 : 0.35}
        />
      </g>
    </svg>
  );
}
