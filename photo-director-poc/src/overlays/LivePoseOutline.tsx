"use client";

import React from "react";
import type { BodyObservation, DetectedSubject } from "../types/composition";

export interface LivePoseOutlineProps {
  /** Whether the composition is aligned/locked */
  isAligned: boolean;
  /** Real detected body observation from MediaPipe Pose */
  body: BodyObservation | null;
  /** Real detected face/head from MediaPipe Face */
  subject: DetectedSubject | null;
}

/**
 * LivePoseOutline: Outlines the ACTUAL detected person's body and posture
 * ONLY when locked in position (isAligned === true).
 * When unaligned, it stays completely invisible with ZERO dummy placeholders.
 */
export default function LivePoseOutline({
  isAligned,
  body,
  subject,
}: LivePoseOutlineProps) {
  // If not locked in position, stay completely hidden (zero viewfinder clutter)
  if (!isAligned) {
    return null;
  }

  // If no person detected, nothing to outline
  if (!body && !subject) {
    return null;
  }

  // Head center and dimensions
  const headX = (subject ? subject.centerX : body ? body.centerX : 0.5) * 100;
  const headY = (subject ? subject.centerY : body ? body.top + body.height * 0.18 : 0.35) * 100;
  const headRadiusX = (subject ? subject.width * 0.5 : 0.09) * 100;
  const headRadiusY = (subject ? subject.height * 0.55 : 0.12) * 100;

  // Body bounds
  const bodyLeft = (body ? body.left : subject ? subject.centerX - subject.width * 0.9 : 0.25) * 100;
  const bodyTop = (body ? body.top : subject ? subject.centerY + subject.height * 0.4 : 0.45) * 100;
  const bodyWidth = (body ? body.width : subject ? subject.width * 1.8 : 0.5) * 100;
  const bodyHeight = (body ? body.height : 0.45) * 100;

  // Shoulder coordinates
  const leftShoulderX = Math.max(0, bodyLeft);
  const rightShoulderX = Math.min(100, bodyLeft + bodyWidth);
  const shoulderY = Math.min(100, headY + headRadiusY * 0.85);
  const torsoBottomY = Math.min(100, bodyTop + bodyHeight);

  return (
    <svg
      className="live-pose-outline-svg"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      style={{
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        pointerEvents: "none",
        zIndex: 3,
        overflow: "visible",
      }}
      aria-hidden="true"
    >
      <style>
        {`
          @keyframes realPoseGlow {
            0%, 100% {
              filter: drop-shadow(0 0 6px rgba(213, 255, 72, 0.9)) drop-shadow(0 0 14px rgba(213, 255, 72, 0.45));
              opacity: 0.95;
            }
            50% {
              filter: drop-shadow(0 0 12px rgba(213, 255, 72, 1)) drop-shadow(0 0 24px rgba(213, 255, 72, 0.65));
              opacity: 1;
            }
          }
          .live-pose-locked {
            animation: realPoseGlow 1.6s ease-in-out infinite;
          }
        `}
      </style>

      <g className="live-pose-locked">
        {/* Head Contour Outline */}
        <ellipse
          cx={headX}
          cy={headY}
          rx={Math.max(4, headRadiusX)}
          ry={Math.max(5, headRadiusY)}
          fill="rgba(213, 255, 72, 0.08)"
          stroke="#d5ff48"
          strokeWidth="1.6"
          strokeDasharray="none"
        />

        {/* Eye-line lock marker */}
        <line
          x1={Math.max(0, headX - headRadiusX * 0.7)}
          y1={headY}
          x2={Math.min(100, headX + headRadiusX * 0.7)}
          y2={headY}
          stroke="#d5ff48"
          strokeWidth="1.2"
          strokeDasharray="2, 2"
          strokeOpacity={0.8}
        />

        {/* Neck & Shoulder Silhouette Path */}
        <path
          d={`
            M ${headX - headRadiusX * 0.35} ${headY + headRadiusY * 0.8}
            L ${headX - headRadiusX * 0.35} ${shoulderY}
            L ${leftShoulderX} ${shoulderY + 3}
            L ${Math.max(0, leftShoulderX + bodyWidth * 0.08)} ${torsoBottomY}
            L ${Math.min(100, rightShoulderX - bodyWidth * 0.08)} ${torsoBottomY}
            L ${rightShoulderX} ${shoulderY + 3}
            L ${headX + headRadiusX * 0.35} ${shoulderY}
            L ${headX + headRadiusX * 0.35} ${headY + headRadiusY * 0.8}
          `}
          fill="rgba(213, 255, 72, 0.05)"
          stroke="#d5ff48"
          strokeWidth="1.8"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      </g>
    </svg>
  );
}
