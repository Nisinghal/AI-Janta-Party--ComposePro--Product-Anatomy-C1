"use client";

import React from "react";
import DirectorCameraScreen from "../camera/DirectorCameraScreen";
import { CompositionProvider } from "../context/CompositionContext";

/**
 * CameraViewfinder serves as the primary camera wrapper,
 * ensuring the composition state and geometric presets are provided
 * across the camera viewfinder and overlays.
 */
export default function CameraViewfinder() {
  return (
    <CompositionProvider>
      <DirectorCameraScreen />
    </CompositionProvider>
  );
}
