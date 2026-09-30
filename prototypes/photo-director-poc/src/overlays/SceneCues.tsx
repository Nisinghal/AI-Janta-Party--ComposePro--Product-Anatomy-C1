"use client";

import React from "react";
import CropWarningOverlay from "./CropWarningOverlay";
import DynamicReticleOverlay from "./DynamicReticleOverlay";

/**
 * Minimal, clean scene cues overlay.
 * Cluttering elements (quality chips, floating compass, static stamps)
 * have been removed to give the user a clear, professional viewfinder.
 */
export default function SceneCues() {
  return (
    <>
      {/* Perimeter edge alerts */}
      <DynamicReticleOverlay />

      {/* Critical crop boundary indicator */}
      <CropWarningOverlay />
    </>
  );
}
