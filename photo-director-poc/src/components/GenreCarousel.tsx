"use client";

import React, { useRef } from "react";
import { User, Utensils, Mountain, Building, Camera } from "lucide-react";
import { useCompositionContext } from "../context/CompositionContext";
import type { GenrePreset } from "../types/composition";

interface GenreOption {
  id: GenrePreset;
  label: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
}

const GENRE_PRESETS: GenreOption[] = [
  { id: "PEOPLE", label: "People & Poses", icon: User },
  { id: "FOOD", label: "Food & Tableware", icon: Utensils },
  { id: "NATURE", label: "Nature & Scenic", icon: Mountain },
  { id: "STREET", label: "Street & Architecture", icon: Building },
  { id: "MACRO", label: "Macro & Detail", icon: Camera },
];

export default function GenreCarousel() {
  const { activePreset, setActivePreset } = useCompositionContext();
  const containerRef = useRef<HTMLDivElement>(null);

  const handleSelect = (preset: GenrePreset, e: React.MouseEvent<HTMLButtonElement>) => {
    if (typeof navigator !== "undefined" && "vibrate" in navigator) {
      try {
        navigator.vibrate(12);
      } catch {
        // Ignore haptics failure if unsupported
      }
    }
    setActivePreset(preset);
    e.currentTarget.scrollIntoView({
      behavior: "smooth",
      inline: "center",
      block: "nearest",
    });
  };

  return (
    <div className="genre-carousel-wrapper" role="region" aria-label="Genre Presets">
      <div
        ref={containerRef}
        className="genre-carousel-dock"
        role="tablist"
        aria-label="Composition Genre Presets"
      >
        {GENRE_PRESETS.map(({ id, label, icon: Icon }) => {
          const isSelected = activePreset === id;
          return (
            <button
              key={id}
              role="tab"
              type="button"
              aria-selected={isSelected}
              onClick={(e) => handleSelect(id, e)}
              className={`genre-preset-pill ${isSelected ? "selected" : ""}`}
            >
              <Icon size={16} className="shrink-0" />
              <span>{label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
