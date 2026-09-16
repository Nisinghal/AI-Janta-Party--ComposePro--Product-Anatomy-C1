# ComposePro MVP Proof of Concept (PoC) Plan

We will build the **ComposePro MVP** focusing on an Android-feasible Proof of Concept (PoC). The goal is to create the foundational UI components—the live camera view, smart overlays, and coaching tooltips—based on modern, premium design patterns found on Mobbin and Refero.

## User Review Required

> [!IMPORTANT]
> Please review the proposed tech stack and UI mockups below. If you approve, I will automatically execute this plan and build the foundational UI components.

## Proposed Tech Stack

To ensure rapid development and full Android camera compatibility for the PoC, we will use **React Native with Expo**:
- **Framework:** Expo (React Native) — Easiest and fastest way to build an Android PoC that requires hardware camera access.
- **Camera Module:** `expo-camera` to render the live camera feed.
- **Styling/UI:** Custom UI components using standard React Native StyleSheet with premium glassmorphism effects, dark mode aesthetics, and smooth animations.

## UI/UX Inspiration & Foundational Components

Based on research into modern smart camera interfaces, the UI will prioritize the camera feed, keeping controls minimal and context-aware.

### 1. Smart Coaching Overlays
The core of the app. We will build transparent overlays that sit on top of the live camera feed.
- **Rule of Thirds Grid:** A subtle, thin grid overlay.
- **Horizon Leveler:** A dynamic line in the center that indicates device tilt.
- **Coaching Tooltip:** A sleek, minimalist "pill" tooltip that provides real-time nudges (e.g., "Tilt down slightly").

![Smart Camera UI Concept](C:\Users\nisht\.gemini\antigravity-ide\brain\06eed479-7685-45ba-b2f2-fe195cb4b813\smart_camera_ui_1789564169590.jpg)

### 2. Genre Presets Carousel
A carousel at the bottom of the screen to quickly switch between coaching modes (Portrait, Food, Landscape, Street).
- **Glassmorphism Base:** A slightly blurred, translucent background for the controls.
- **Premium Capture Button:** A prominent, aesthetic shutter button.

![Presets UI Concept](C:\Users\nisht\.gemini\antigravity-ide\brain\06eed479-7685-45ba-b2f2-fe195cb4b813\camera_presets_ui_1789564182231.jpg)

## Proposed Changes

### 1. App Initialization
- Run `npx create-expo-app@latest composepro-app` in the `D:\ComposePro` directory.
- Install dependencies: `expo-camera`, `expo-status-bar`, and icons.

### 2. Foundational UI Components
I will create the following component structure:

#### [NEW] `src/components/CameraView.js`
The core component that wraps the `expo-camera` feed.

#### [NEW] `src/components/Overlays/GridOverlay.js`
Renders the Rule of Thirds grid.

#### [NEW] `src/components/Overlays/HorizonLevel.js`
Renders the central horizon line. In the PoC, this will be visually represented and animated.

#### [NEW] `src/components/Overlays/CoachingTooltip.js`
The glassmorphism pill that displays text prompts like "Tilt down slightly".

#### [NEW] `src/components/Controls/BottomControls.js`
The bottom section containing the Genre Carousel (Portrait, Food, Landscape) and the Shutter Button.

## Verification Plan

### Manual Verification
- We will run the Expo development server.
- You can scan the QR code using the Expo Go app on your Android phone to instantly test the UI overlays over your live camera feed.
- We will verify that the layout looks premium, the grid aligns correctly, and the UI components match the "expert girlfriend" aesthetic.
