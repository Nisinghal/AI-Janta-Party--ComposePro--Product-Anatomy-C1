"use client";

import React, { useRef } from "react";
import { useCompositionContext } from "../context/CompositionContext";
import type { GenrePreset } from "../types/composition";

export interface PresetCarouselProps {
  /** Optional custom CSS class name */
  className?: string;
  /** Optional callback triggered on preset selection */
  onSelectPreset?: (preset: GenrePreset) => void;
}

interface PresetItem {
  id: GenrePreset;
  label: string;
}

const PRESET_OPTIONS: PresetItem[] = [
  { id: "PEOPLE", label: "People" },
  { id: "FOOD", label: "Food" },
  { id: "NATURE", label: "Nature" },
  { id: "STREET", label: "Street" },
  { id: "MACRO", label: "Macro" },
];

export default function PresetCarousel({ className = "", onSelectPreset }: PresetCarouselProps) {
  const { activePreset, setActivePreset } = useCompositionContext();
  const containerRef = useRef<HTMLDivElement>(null);

  const handleSelect = (preset: GenrePreset, e: React.MouseEvent<HTMLButtonElement>) => {
    // Subtle haptic response on supported mobile devices
    if (typeof navigator !== "undefined" && "vibrate" in navigator) {
      try {
        navigator.vibrate(12);
      } catch {
        // Ignore haptics failure if unsupported or blocked
      }
    }

    // Immediately update active preset state without affecting camera stream
    setActivePreset(preset);
    onSelectPreset?.(preset);

    // Smoothly center the active pill within the dock
    e.currentTarget.scrollIntoView({
      behavior: "smooth",
      inline: "center",
      block: "nearest",
    });
  };

  return (
    <nav
      className={`preset-carousel-container ${className}`}
      role="region"
      aria-label="Composition Genre Presets"
      style={{
        display: "flex",
        justifyContent: "center",
        width: "100%",
        padding: "0 0.75rem",
        marginBottom: "0.65rem",
      }}
    >
      <div
        ref={containerRef}
        role="tablist"
        aria-label="Select camera composition preset"
        style={{
          display: "flex",
          alignItems: "center",
          gap: "0.35rem",
          maxWidth: "100%",
          overflowX: "auto",
          scrollbarWidth: "none",
          WebkitOverflowScrolling: "touch",
          padding: "0.3rem 0.4rem",
          borderRadius: "999px",
          background: "rgba(0, 0, 0, 0.52)",
          border: "1px solid rgba(255, 255, 255, 0.12)",
          backdropFilter: "blur(18px)",
          WebkitBackdropFilter: "blur(18px)",
          boxShadow: "0 8px 30px rgba(0, 0, 0, 0.35)",
        }}
      >
        <style>
          {`
            .preset-carousel-container div::-webkit-scrollbar {
              display: none;
            }
          `}
        </style>

        {PRESET_OPTIONS.map(({ id, label }) => {
          const isSelected = activePreset === id;
          return (
            <button
              key={id}
              role="tab"
              type="button"
              aria-selected={isSelected}
              onClick={(e) => handleSelect(id, e)}
              style={{
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                minHeight: "38px",
                minWidth: "64px",
                padding: "0.45rem 1.05rem",
                borderRadius: "999px",
                border: isSelected
                  ? "1px solid rgba(213, 255, 72, 0.5)"
                  : "1px solid transparent",
                background: isSelected ? "rgba(213, 255, 72, 0.16)" : "transparent",
                color: isSelected ? "#d5ff48" : "rgba(255, 255, 255, 0.65)",
                boxShadow: isSelected ? "0 0 16px rgba(213, 255, 72, 0.28)" : "none",
                fontSize: "0.78rem",
                fontWeight: isSelected ? 700 : 500,
                letterSpacing: "0.04em",
                textTransform: "uppercase",
                whiteSpace: "nowrap",
                cursor: "pointer",
                transition: "all 0.22s cubic-bezier(0.16, 1, 0.3, 1)",
                userSelect: "none",
                WebkitTapHighlightColor: "transparent",
              }}
            >
              {label}
            </button>
          );
        })}
      </div>
    </nav>
  );
}
