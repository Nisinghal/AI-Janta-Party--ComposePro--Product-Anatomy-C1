# AI Photo Director — local setup

## Requirements

- Node.js 22.13 or newer
- npm (included with Node.js)
- A modern browser with camera support (Chrome, Edge, or Safari)

## Run locally

From this folder:

```bash
npm run install:ci
npm run dev
```

Open the local URL printed in the terminal. Allow camera access when the browser asks.

For camera access on a physical phone, browsers require a secure origin. Use an HTTPS local-development tunnel, or deploy the app to an HTTPS host, then open that URL on the phone.

## Production check

```bash
npm run build
npm start
```

## Main implementation files

- `src/camera/DirectorCameraScreen.tsx` — camera, MediaPipe detection, controls, and capture flow
- `src/vision/sceneAnalysis.ts` — lighting, background, separation, and scene-quality analysis
- `src/composition/compositionEngine.ts` — composition scoring and recommendations
- `src/composition/compositionConfig.ts` — tunable composition thresholds
- `src/overlays/SceneCues.tsx` — visual guidance and color-block overlays

All computer-vision model files needed by the current build are included under `public/mediapipe/`.

