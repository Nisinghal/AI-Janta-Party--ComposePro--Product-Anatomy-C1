"use client";

import React, { useRef } from "react";
import { useCompositionContext } from "../context/CompositionContext";
import type { GenrePreset } from "../types/composition";

export interface PresetCarouselProps {
  className?: string;
  onSelectPreset?: (preset: GenrePreset) => void;
}

interface PresetItem {
  id: GenrePreset;
  label: string;
  emoji: string;
}

const PRESET_OPTIONS: PresetItem[] = [
  { id: "FLAT_LAY", label: "Flat Lay", emoji: "🍽️" },
  { id: "HERO_SHOT", label: "Hero", emoji: "🍔" },
  { id: "TALL_STACK", label: "Stack", emoji: "🥞" },
];

export default function PresetCarousel({ className = "", onSelectPreset }: PresetCarouselProps) {
  const { activePreset, setActivePreset } = useCompositionContext();
  const containerRef = useRef<HTMLDivElement>(null);

  const handleSelect = (preset: GenrePreset, e: React.MouseEvent<HTMLButtonElement>) => {
    if (typeof navigator !== "undefined" && "vibrate" in navigator) {
      try { navigator.vibrate(12); } catch {}
    }
    setActivePreset(preset);
    onSelectPreset?.(preset);
    e.currentTarget.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
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
        padding: "0 1rem",
        marginBottom: "0.75rem",
      }}
    >
      <div
        ref={containerRef}
        role="tablist"
        aria-label="Select camera composition preset"
        style={{
          display: "flex",
          alignItems: "center",
          gap: "0.5rem",
          padding: "0.3rem",
          borderRadius: "999px",
          background: "rgba(0, 0, 0, 0.45)",
          backdropFilter: "blur(16px)",
          WebkitBackdropFilter: "blur(16px)",
        }}
      >
        <style>
          {`
            .preset-carousel-container div::-webkit-scrollbar {
              display: none;
            }
          `}
        </style>

        {PRESET_OPTIONS.map(({ id, label, emoji }) => {
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
                gap: "0.4rem",
                minHeight: "36px",
                padding: "0.4rem 1rem",
                borderRadius: "999px",
                border: "none",
                background: isSelected ? "#FF6B6B" : "transparent",
                color: isSelected ? "#fff" : "rgba(255, 255, 255, 0.7)",
                boxShadow: isSelected ? "0 4px 16px rgba(255,107,107,.4)" : "none",
                fontSize: "0.8rem",
                fontWeight: isSelected ? 700 : 500,
                whiteSpace: "nowrap",
                cursor: "pointer",
                transition: "all 0.2s cubic-bezier(0.16, 1, 0.3, 1)",
                userSelect: "none",
                WebkitTapHighlightColor: "transparent",
              }}
            >
              <span style={{ fontSize: "1rem" }}>{emoji}</span>
              {label}
            </button>
          );
        })}
      </div>
    </nav>
  );
}

