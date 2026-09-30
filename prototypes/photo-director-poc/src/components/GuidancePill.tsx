"use client";

import React from "react";
import {
  Check,
  Navigation,
  AlertTriangle,
  Sun,
  ArrowUp,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useCompositionContext } from "../context/CompositionContext";
import type { CompositionGuidance, RuleEvaluationResult } from "../types/composition";

export interface GuidancePillProps {
  /** Optional legacy guidance data */
  guidance?: CompositionGuidance;
  /** Direct rule evaluation result from multi-genre composition engine */
  ruleResult?: RuleEvaluationResult | null;
}

export default function GuidancePill({ guidance, ruleResult }: GuidancePillProps) {
  const {
    opportunity,
    recommendation: contextRec,
    isAligned: contextAligned,
    guidanceNudge,
    ruleResult: contextRuleResult,
  } = useCompositionContext();

  const activeRuleResult = ruleResult ?? contextRuleResult;

  // Determine the primary guidance text
  const recommendation = opportunity?.recommendation ?? contextRec;
  const rawText: string | null =
    activeRuleResult?.guidanceText ||
    (recommendation ? recommendation.pillText : null) ||
    opportunity?.actionNudge?.text ||
    guidance?.message ||
    guidanceNudge?.text ||
    null;

  if (!rawText) {
    return null;
  }

  // Alignment check
  const isAligned: boolean =
    activeRuleResult !== null && activeRuleResult !== undefined
      ? activeRuleResult.isAligned
      : opportunity !== null
      ? opportunity.isOptimized
      : contextAligned;

  const priority = recommendation?.priority ?? (isAligned ? "locked" : "coaching");

  // Determine translucent glassmorphism styles
  let borderColor = "rgba(255, 255, 255, 0.16)";
  let bgColor = "rgba(0, 0, 0, 0.68)";
  let textColor = "#ffffff";
  let shadow = "0 8px 24px rgba(0, 0, 0, 0.4)";

  if (isAligned) {
    borderColor = "#d5ff48";
    bgColor = "rgba(18, 24, 8, 0.90)";
    textColor = "#d5ff48";
    shadow = "0 0 22px rgba(213, 255, 72, 0.45), 0 8px 32px rgba(0, 0, 0, 0.5)";
  } else if (priority === "critical") {
    borderColor = "rgba(239, 68, 68, 0.9)";
    bgColor = "rgba(42, 12, 12, 0.90)";
    textColor = "#fca5a5";
    shadow = "0 0 20px rgba(239, 68, 68, 0.5), 0 8px 28px rgba(0, 0, 0, 0.5)";
  } else if (priority === "warning") {
    borderColor = "rgba(245, 158, 11, 0.85)";
    bgColor = "rgba(38, 24, 10, 0.90)";
    textColor = "#fcd34d";
    shadow = "0 0 16px rgba(245, 158, 11, 0.4), 0 8px 24px rgba(0, 0, 0, 0.4)";
  }

  // Render icon based on state / alignment
  const renderIcon = () => {
    if (isAligned) {
      return (
        <span
          className="ready-badge-indicator"
          style={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            width: "1.35rem",
            height: "1.35rem",
            borderRadius: "50%",
            background: "rgba(213, 255, 72, 0.22)",
            color: "#d5ff48",
            boxShadow: "0 0 8px rgba(213, 255, 72, 0.4)",
          }}
        >
          <Check size={14} strokeWidth={3} aria-hidden />
        </span>
      );
    }

    if (activeRuleResult?.state) {
      switch (activeRuleResult.state) {
        case "MOVE_UP":
          return <ArrowUp size={15} className="shrink-0 text-white/80" aria-hidden />;
        case "MOVE_DOWN":
          return <ArrowDown size={15} className="shrink-0 text-white/80" aria-hidden />;
        case "MOVE_LEFT":
          return <ArrowLeft size={15} className="shrink-0 text-white/80" aria-hidden />;
        case "MOVE_RIGHT":
          return <ArrowRight size={15} className="shrink-0 text-white/80" aria-hidden />;
        case "MOVE_CLOSER":
          return <ZoomIn size={15} className="shrink-0 text-white/80" aria-hidden />;
        case "MOVE_BACK":
          return <ZoomOut size={15} className="shrink-0 text-white/80" aria-hidden />;
        default:
          break;
      }
    }

    const lower = rawText.toLowerCase();
    if (lower.includes("light") || lower.includes("sun") || lower.includes("backlight")) {
      return <Sun size={15} className="shrink-0" style={{ color: textColor }} aria-hidden />;
    }

    if (priority === "critical" || priority === "warning") {
      return <AlertTriangle size={15} className="shrink-0" style={{ color: textColor }} aria-hidden />;
    }

    return (
      <Navigation
        size={15}
        className="shrink-0 text-white/70"
        style={{ transform: "rotate(45deg)" }}
        aria-hidden
      />
    );
  };

  return (
    <div
      className={`gps-guidance-pill ${isAligned ? "is-ready is-locked" : `priority-${priority}`}`}
      role="status"
      aria-live="polite"
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: "0.55rem",
        minHeight: "2.75rem",
        padding: "0.5rem 1.15rem",
        borderRadius: "999px",
        background: bgColor,
        backdropFilter: "blur(18px)",
        WebkitBackdropFilter: "blur(18px)",
        border: `1.5px solid ${borderColor}`,
        boxShadow: shadow,
        color: textColor,
        transition: "all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1)",
        userSelect: "none",
        pointerEvents: "none",
      }}
    >
      <style>
        {`
          @keyframes lockPulse {
            0% {
              transform: scale(0.96);
              box-shadow: 0 0 0 0 rgba(213, 255, 72, 0.7);
              border-color: #d5ff48;
              background: rgba(18, 24, 8, 0.92);
            }
            35% {
              transform: scale(1.06);
              box-shadow: 0 0 26px 6px rgba(213, 255, 72, 0.85);
              border-color: #d5ff48;
            }
            100% {
              transform: scale(1);
              box-shadow: 0 0 18px 0 rgba(213, 255, 72, 0.4);
              border-color: #d5ff48;
            }
          }
          .is-ready {
            animation: lockPulse 1.5s cubic-bezier(0.34, 1.56, 0.64, 1);
          }
        `}
      </style>

      {renderIcon()}

      <span
        style={{
          fontSize: "0.85rem",
          fontWeight: isAligned ? 700 : 600,
          letterSpacing: "0.015em",
          lineHeight: 1.2,
          whiteSpace: "nowrap",
        }}
      >
        {rawText}
      </span>
    </div>
  );
}
