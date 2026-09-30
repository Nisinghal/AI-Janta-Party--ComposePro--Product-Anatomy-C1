/**
 * useAutoCrop — Composition-aware auto-crop suggestion engine.
 *
 * Photography principles applied:
 * ─────────────────────────────────────────────────────────────────────────────
 * 1. SUBJECT ISOLATION: Crops away distracting background, focusing on the
 *    food cluster. This is the #1 improvement most amateur food photos need.
 *
 * 2. NEGATIVE SPACE RATIO: Each preset defines an ideal ratio of breathing
 *    room around the subject:
 *      - Flat Lay:   30% padding (airy, editorial feel)
 *      - Hero Shot:  25% padding (tight but not claustrophobic)
 *      - Tall Stack: 20% horizontal / 30% vertical (tall & dramatic)
 *
 * 3. RULE OF THIRDS NUDGE: The crop isn't naively centered. For Hero Shot,
 *    the subject is nudged to the lower-third intersection. For Tall Stack,
 *    the subject stays vertically centered but horizontally centered.
 *
 * 4. ASPECT RATIO ENFORCEMENT: Each preset has a preferred output ratio:
 *      - Flat Lay:   1:1  (Instagram square, overhead symmetry)
 *      - Hero Shot:  4:5  (Instagram portrait, emphasis on plate)
 *      - Tall Stack: 9:16 (Stories / vertical, dramatic height)
 *
 * 5. SIGNIFICANCE THRESHOLD: The crop is only suggested when it would crop
 *    away ≥ 25% of the frame area. If the subject already fills the frame
 *    well, no suggestion is shown.
 *
 * 6. TEMPORAL STABILITY: The crop rectangle is exponentially smoothed over
 *    frames to prevent jittery jumping. A new suggestion must be stable for
 *    ≥ 1 second before it's surfaced to the user.
 * ─────────────────────────────────────────────────────────────────────────────
 */

import { useRef, useState, useCallback } from "react";
import type { GenrePreset } from "../types/composition";

/** Normalized 0–1 rectangle in video coordinate space */
export interface CropRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface AutoCropSuggestion {
  /** The suggested crop in normalized 0–1 coords */
  crop: CropRect;
  /** How much of the original frame this crop keeps (0–1). Lower = more dramatic crop. */
  keepRatio: number;
  /** Whether this suggestion is considered significant enough to show */
  isSignificant: boolean;
  /** Human-readable label for why this crop is better */
  reason: string;
}

// Preset-specific crop configuration
const CROP_CONFIG: Record<GenrePreset, {
  padX: number;      // horizontal padding ratio around subject
  padY: number;      // vertical padding ratio around subject
  aspect: number;    // target width/height ratio
  thirdsBias: { x: number; y: number }; // how much to nudge toward a thirds intersection (0 = center, 0.33 = full third)
  minKeep: number;   // minimum keepRatio before suggesting (area threshold)
}> = {
  FLAT_LAY: {
    padX: 0.30,
    padY: 0.30,
    aspect: 1.0,        // 1:1 square
    thirdsBias: { x: 0, y: 0 }, // centered (symmetry matters for overhead)
    minKeep: 0.75,
  },
  HERO_SHOT: {
    padX: 0.25,
    padY: 0.25,
    aspect: 0.8,        // 4:5 portrait
    thirdsBias: { x: 0, y: 0.12 }, // nudge subject slightly below center
    minKeep: 0.72,
  },
  TALL_STACK: {
    padX: 0.20,
    padY: 0.30,
    aspect: 0.5625,     // 9:16 vertical
    thirdsBias: { x: 0, y: 0 },
    minKeep: 0.68,
  },
};

// EMA smoothing factor (0 = no smoothing, 1 = no update)
const SMOOTH_ALPHA = 0.15;
const STABILITY_MS = 1000; // how long crop must be stable before surfacing

function lerp(a: number, b: number, t: number) {
  return a + (b - a) * t;
}

function clamp01(v: number) {
  return Math.max(0, Math.min(1, v));
}

export function useAutoCrop() {
  const [suggestion, setSuggestion] = useState<AutoCropSuggestion | null>(null);
  const smoothedCropRef = useRef<CropRect | null>(null);
  const candidateRef = useRef<{ crop: CropRect; since: number } | null>(null);

  /**
   * Call this each analysis frame with the current detected objects.
   * objects: raw MediaPipe detections array
   * videoWidth / videoHeight: the video element's native resolution
   * preset: the currently active GenrePreset
   */
  const updateCropSuggestion = useCallback((
    objects: any[],
    videoWidth: number,
    videoHeight: number,
    preset: GenrePreset,
  ) => {
    const config = CROP_CONFIG[preset];

    // ── Step 1: Compute subject cluster bounding box ──────────────────
    // Union of all detected food-relevant objects (ignore dining table)
    const foodObjects = objects.filter(o => {
      const name = (o.categories?.[0]?.categoryName || o.label || "").toLowerCase();
      return name !== "dining table"; // table is context, not the subject
    });

    if (foodObjects.length === 0) {
      // No food → no crop suggestion
      if (suggestion !== null) setSuggestion(null);
      smoothedCropRef.current = null;
      candidateRef.current = null;
      return;
    }

    let minX = 1, minY = 1, maxX = 0, maxY = 0;
    for (const obj of foodObjects) {
      const b = obj.boundingBox;
      if (!b) continue;
      const ox = (b.originX ?? b.x ?? 0) / videoWidth;
      const oy = (b.originY ?? b.y ?? 0) / videoHeight;
      const ow = (b.width ?? 0) / videoWidth;
      const oh = (b.height ?? 0) / videoHeight;
      minX = Math.min(minX, ox);
      minY = Math.min(minY, oy);
      maxX = Math.max(maxX, ox + ow);
      maxY = Math.max(maxY, oy + oh);
    }

    const subjectW = maxX - minX;
    const subjectH = maxY - minY;
    const subjectCX = (minX + maxX) / 2;
    const subjectCY = (minY + maxY) / 2;

    // ── Step 2: Add preset-specific padding ───────────────────────────
    const paddedW = subjectW * (1 + config.padX * 2);
    const paddedH = subjectH * (1 + config.padY * 2);

    // ── Step 3: Enforce aspect ratio ──────────────────────────────────
    // config.aspect = w/h, so targetH = targetW / aspect
    let cropW: number, cropH: number;
    if (paddedW / paddedH > config.aspect) {
      // Subject is wider than target aspect → expand height
      cropW = paddedW;
      cropH = paddedW / config.aspect;
    } else {
      // Subject is taller than target aspect → expand width
      cropH = paddedH;
      cropW = paddedH * config.aspect;
    }

    // Ensure minimum size (don't suggest absurdly tight crops)
    cropW = Math.max(cropW, 0.3);
    cropH = Math.max(cropH, 0.3);

    // ── Step 4: Apply rule-of-thirds bias ─────────────────────────────
    // Shift the crop center so the subject lands at the desired intersection
    const cropCX = subjectCX - config.thirdsBias.x * cropW;
    const cropCY = subjectCY + config.thirdsBias.y * cropH;

    // ── Step 5: Compute final rect and clamp to frame ─────────────────
    let rawCrop: CropRect = {
      x: clamp01(cropCX - cropW / 2),
      y: clamp01(cropCY - cropH / 2),
      w: Math.min(cropW, 1),
      h: Math.min(cropH, 1),
    };
    // Ensure it doesn't extend past edges
    if (rawCrop.x + rawCrop.w > 1) rawCrop.x = 1 - rawCrop.w;
    if (rawCrop.y + rawCrop.h > 1) rawCrop.y = 1 - rawCrop.h;
    rawCrop.x = Math.max(0, rawCrop.x);
    rawCrop.y = Math.max(0, rawCrop.y);

    // ── Step 6: Temporal smoothing (EMA) ──────────────────────────────
    if (smoothedCropRef.current) {
      smoothedCropRef.current = {
        x: lerp(smoothedCropRef.current.x, rawCrop.x, SMOOTH_ALPHA),
        y: lerp(smoothedCropRef.current.y, rawCrop.y, SMOOTH_ALPHA),
        w: lerp(smoothedCropRef.current.w, rawCrop.w, SMOOTH_ALPHA),
        h: lerp(smoothedCropRef.current.h, rawCrop.h, SMOOTH_ALPHA),
      };
    } else {
      smoothedCropRef.current = rawCrop;
    }

    const crop = smoothedCropRef.current;
    const keepRatio = crop.w * crop.h; // area fraction of original

    // ── Step 7: Significance check ────────────────────────────────────
    const isSignificant = keepRatio < config.minKeep;

    // ── Step 8: Stability gate — must be significant for ≥1s ──────────
    const now = performance.now();
    if (isSignificant) {
      if (!candidateRef.current) {
        candidateRef.current = { crop, since: now };
      }
      const stableMs = now - candidateRef.current.since;
      if (stableMs >= STABILITY_MS) {
        const reason = keepRatio < 0.5
          ? "Tighter crop isolates your dish beautifully"
          : keepRatio < 0.65
          ? "Cropping removes distracting background"
          : "Subtle reframe improves composition";

        setSuggestion({ crop, keepRatio, isSignificant: true, reason });
      }
    } else {
      candidateRef.current = null;
      if (suggestion?.isSignificant) {
        setSuggestion({ crop, keepRatio, isSignificant: false, reason: "" });
      }
    }
  }, [suggestion]);

  const dismissSuggestion = useCallback(() => {
    setSuggestion(null);
    candidateRef.current = null;
  }, []);

  return { suggestion, updateCropSuggestion, dismissSuggestion };
}
