# Product Requirements Document (PRD): ComposePro

## 1. Product Overview
**ComposePro** is a smart camera application that acts as a real-time, visual "expert girlfriend/coach" right in the smartphone camera view. It helps everyday users capture stunning, well-composed photos by providing deterministic overlays (grids, horizons) and real-time coaching prompts (lighting, angles) *before* the shutter is pressed. 

**Target Audience:**
- Social Sharers (18–34): Quick, aesthetic everyday photos.
- Travel & Food Creators (22–40): Aesthetic consistency and fast workflow.
- Enthusiast Upgraders (25–50): Deliberate practice and improvement.
- Local Pros (28–55): Faster setups and reduced reshoots.

---

## 2. Core Components

### 2.1 Camera View & Engine
- **Viewfinder:** A real-time, zero-latency camera feed using native camera APIs (e.g., `expo-camera`).
- **Capture Controls:** Premium, glassmorphism UI for shutter button, zoom (1x, 2x, 3x), and flash toggle.

### 2.2 Smart Coaching Overlays (Visual Guidance)
- **Rule-of-Thirds Grid:** A subtle screen overlay for subject alignment.
- **Horizon Leveler:** A dynamic center line connected to the device's gyroscope that turns green when the phone is perfectly level.
- **Subject Placement Indicators:** Visual bounding boxes or dots showing exactly where the primary subject should be positioned.

### 2.3 Real-Time Prompt Engine (Text Guidance)
- **Glassmorphism Tooltips:** Floating UI pills that deliver concise (3-6 words) actionable coaching.
- **Angle/Height Prompts:** e.g., "Tilt down slightly", "Try a lower angle".
- **Light Direction Prompts:** e.g., "Shift so light hits from side", "Reduce backlight".

### 2.4 Contextual Genre Presets
- **Bottom Carousel:** Allows the user to select the shooting context.
  - *Portrait:* Prioritizes eye level and look room.
  - *Landscape:* Prioritizes sky/land balance and horizon placement.
  - *Food:* Prioritizes overhead 45-degree angles.

---

## 3. Phases of Development

### Phase 1: Proof of Concept (PoC) & MVP
*Focus: Establishing the foundational "in-the-moment" overlays on Android using React Native.*
- Initialize React Native (Expo) project.
- Implement the basic Camera View component.
- Build the static Grid Overlay and Genre Preset Carousel UI.
- Implement the Horizon Leveler using the device's gyroscope to show live tilt.
- Create static/mock UI for the floating Coaching Tooltips to demonstrate the UX.

### Phase 2: Basic Intelligence & User Flows
*Focus: Connecting the visual components to actual logic and creating a full user session.*
- Implement Subject Placement Detection (using basic ML Kit / Vision APIs) to dynamically highlight the subject.
- Build the Prompt Engine to trigger textual cues based on lighting sensors (e.g., detecting heavy backlight) and device angle.
- Ensure zero-latency rendering so the camera doesn't lag while prompts are active.
- Allow users to capture and save the image to their device's camera roll.

### Phase 3: Advanced Coaching & Monetization
*Focus: Enhancing AI capabilities and introducing premium features.*
- **V2 AI Models:** Real-time distraction highlighting (e.g., "Photobomber in background") and dynamic lighting analysis.
- **Progress Tracking:** Simple streaks and before/after comparisons to build user confidence.
- **Pro Tier ($6.99/mo):** Unlock advanced analytics, consistency scores, and unlimited genre coaching.
- **Community Features:** "One Rule Per Day" challenges and shareable growth carousels for social media.
