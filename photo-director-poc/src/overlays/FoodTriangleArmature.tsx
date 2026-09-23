"use client";

import React from "react";
import { useCompositionContext } from "../context/CompositionContext";

export default function FoodTriangleArmature() {
  const { isAligned, activeArmature } = useCompositionContext();

  // Normalized 0-1 coordinates mapped to 0-100 viewBox percentages
  const targetNodes =
    activeArmature?.targetPoints && activeArmature.targetPoints.length >= 3
      ? activeArmature.targetPoints
      : [
          { x: 0.5, y: 0.35, label: "pyramid-apex" },
          { x: 0.28, y: 0.72, label: "pyramid-left" },
          { x: 0.72, y: 0.72, label: "pyramid-right" },
        ];

  const [p1, p2, p3] = targetNodes.map((pt) => ({
    x: pt.x * 100,
    y: pt.y * 100,
    label: pt.label,
  }));

  const strokeColor = isAligned ? "#d5ff48" : "rgba(255, 255, 255, 0.25)";
  const strokeWidth = isAligned ? 1.5 : 0.8;
  const filterStyle = isAligned ? "drop-shadow(0 0 6px #d5ff48)" : "none";

  return (
    <svg
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      className="food-triangle-armature"
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
      <defs>
        <filter id="armature-glow" x="-20%" y="-20%" width="140%" height="140%">
          <feDropShadow dx="0" dy="0" stdDeviation="2.5" floodColor="#d5ff48" floodOpacity="0.8" />
        </filter>
      </defs>

      {/* Triangular anchor connecting main dishes and drinks */}
      <polygon
        points={`${p1.x},${p1.y} ${p2.x},${p2.y} ${p3.x},${p3.y}`}
        fill={isAligned ? "rgba(213, 255, 72, 0.08)" : "transparent"}
        stroke={strokeColor}
        strokeWidth={strokeWidth}
        strokeDasharray="4, 4"
        strokeLinejoin="round"
        style={{
          transition: "stroke 0.3s ease, stroke-width 0.3s ease, fill 0.3s ease, filter 0.3s ease",
          filter: filterStyle,
        }}
      />

      {/* Subtle 45-degree dashed leading table edges extending to bottom corners */}
      <line
        x1={p2.x}
        y1={p2.y}
        x2={0}
        y2={100}
        stroke={strokeColor}
        strokeWidth={strokeWidth * 0.75}
        strokeDasharray="4, 4"
        strokeOpacity={isAligned ? 0.85 : 0.45}
        style={{
          transition: "stroke 0.3s ease, stroke-width 0.3s ease, filter 0.3s ease",
          filter: isAligned ? "drop-shadow(0 0 4px #d5ff48)" : "none",
        }}
      />
      <line
        x1={p3.x}
        y1={p3.y}
        x2={100}
        y2={100}
        stroke={strokeColor}
        strokeWidth={strokeWidth * 0.75}
        strokeDasharray="4, 4"
        strokeOpacity={isAligned ? 0.85 : 0.45}
        style={{
          transition: "stroke 0.3s ease, stroke-width 0.3s ease, filter 0.3s ease",
          filter: isAligned ? "drop-shadow(0 0 4px #d5ff48)" : "none",
        }}
      />

      {/* 3 target vertex nodes */}
      {[p1, p2, p3].map((pt, idx) => (
        <g key={pt.label || idx} style={{ transition: "all 0.3s ease" }}>
          {/* Target halo circle */}
          <circle
            cx={pt.x}
            cy={pt.y}
            r={isAligned ? 3.6 : 3.0}
            fill={isAligned ? "rgba(213, 255, 72, 0.18)" : "rgba(255, 255, 255, 0.04)"}
            stroke={strokeColor}
            strokeWidth={strokeWidth}
            style={{
              transition: "stroke 0.3s ease, stroke-width 0.3s ease, fill 0.3s ease, filter 0.3s ease",
              filter: filterStyle,
            }}
          />
          {/* Center vertex pin */}
          <circle
            cx={pt.x}
            cy={pt.y}
            r={1}
            fill={strokeColor}
            style={{
              transition: "fill 0.3s ease",
            }}
          />
        </g>
      ))}
    </svg>
  );
}
