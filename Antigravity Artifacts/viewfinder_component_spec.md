# Component 2.1: Camera Viewfinder (Result Card)

*Since Figma's API restricts automated canvas edits, this document serves as the programmatic "Figma File" component specification for the Viewfinder structure.*

## Full Anatomy (Nothing Stubbed)

**1. Root Layer:**
- Full-bleed Live Camera Feed (Z-index 0)

**2. Top Area:**
- **Top Toolbar:** 
  - `[Flash Icon]` (Left, 16px margin)
  - `[Info Icon]` (Center)
  - `[Close 'X']` (Right, 16px margin)
  - *Style:* White, 1px stroke icons.

**3. Center Area (Guidance & Focus):**
- **AR Coaching Tooltip:** 
  - *Position:* Asymmetric (Top-Left quadrant).
  - *Style:* Frosted glassmorphism pill, 12px vertical / 20px horizontal padding.
  - *Content:* "Tilt down slightly" (16pt, Medium, +2% tracking).
- **Focus Brackets:** 
  - *Position:* Center.
  - *Style:* `[ ]` Corner brackets, 1px thick, stark white. Padded 64px from screen edges.
- **Horizon Leveler:**
  - *Position:* Snapping center line.
  - *Style:* 1px thin solid line.

**4. Bottom Area (Capture Controls):**
- **Control Panel:**
  - *Style:* Floating glassmorphism rounded panel (24px radius), hovering 16px above the bottom edge.
- **Zoom Controls:** 
  - *Content:* `1x` `2x` `3x`
  - *Style:* Geometric Sans-serif, 14pt.
- **Shutter Button:** 
  - *Position:* Bottom center, inside the control panel.
  - *Style:* Glassmorphism gradient fill (subtle transparency) to match the PRD's "premium glassmorphism UI for shutter button".

---

## The Three Variants (Status Treatments)

*Layout remains perfectly identical across all three; only the status treatment changes.*

### Variant A: Idle / Default
- **Focus Brackets:** Stark White, 60% opacity.
- **Horizon Leveler:** White, 50% opacity, slightly off-center.
- **AR Tooltip:** Hidden.
- **Zoom Controls:** `1x` is Bold (100% opacity), `2x` and `3x` are Regular (60% opacity).
- **Shutter:** Glassmorphism gradient, default state.

### Variant B: Scanning / Active Guidance
- **Focus Brackets:** Pulsing White (opacity shifts 60% -> 100%).
- **Horizon Leveler:** White, moving towards center.
- **AR Tooltip:** Visible ("Tilt down slightly"). Glassmorphism pill blurs the background feed.
- **Zoom Controls:** Unchanged.
- **Shutter:** Unchanged.

### Variant C: Locked / Success
- **Focus Brackets:** Snapped to Gold/Yellow accent color (100% opacity).
- **Horizon Leveler:** Snaps dead center, color turns **Green** (as defined in PRD Section 2.2), thickens to 2px.
- **AR Tooltip:** Text updates to "Hold steady" (Gold/Yellow text).
- **Zoom Controls:** Unchanged.
- **Shutter:** Inner circle scales down slightly (active depression).
