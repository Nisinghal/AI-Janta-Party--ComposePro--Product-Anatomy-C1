export const compositionConfig = {
  targetFaceCenterX: 0.38, targetFaceCenterY: 0.34,
  acceptableXMin: 0.30, acceptableXMax: 0.47,
  acceptableYMin: 0.24, acceptableYMax: 0.44,
  desiredFaceHeightMin: 0.16, desiredFaceHeightMax: 0.31,
  smoothingAlpha: 0.24, instructionPersistenceMs: 420,
  readyConfirmationMs: 650, analysisIntervalMs: 90,
} as const;
