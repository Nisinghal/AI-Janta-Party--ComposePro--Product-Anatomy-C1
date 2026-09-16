# ComposePro: Viewfinder Design Direction (Section 2.1)

Based directly on the 9 visual references you've uploaded (the previous 5 and the new 4), here is the fully abstracted structural and typographic design direction for the ComposePro Viewfinder. 

---

## 1. Core Visual References

The design language we are extracting from your uploaded references is **"Technical Modernism mixed with AR Spatial Awareness"**—a blend of tactile, physical camera controls and sleek, immersive augmented reality overlays.

### Reference Abstractions (Updated):
1. **The Pro Dashboard & Bottom Sheets:** High-contrast, technical data readouts using structured, monospace-style typography. For highly complex modes (like Bracketing or Pro Mode), the UI uses a heavy, dark bottom sheet that anchors the lower half of the screen to organize dense information (thumbnails, EV steps) without cluttering the viewfinder.
2. **The Floating Island Controls:** Basic camera settings (Aspect, Timer) are housed in floating, curved, semi-transparent dark panels. 
3. **Immersive AR Overlays & Tooltips:** The use of heavy frosted glass (glassmorphism) or stark white speech bubbles for coaching tooltips (e.g., "Move your device around"). These float asymmetrically (e.g., top-left) to guide the user without blocking the center. 
4. **Stark Viewfinder Geometry:** 
   - *For Composition:* Ultra-minimalist 1px-thin rule-of-thirds grid lines and a simple double-ring geometric reticle.
   - *For Object Scanning:* Thick, stark white corner brackets (`[ ]`) to indicate a specific scanning zone, paired with stark white iconography in the center of the screen (e.g., a hand holding a phone) to guide physical user movement.
5. **Circular AR Carousels:** For selecting presets or objects in space, the UI uses circular image thumbnails floating inside a pill-shaped frosted glass dock at the bottom of the screen.

---

## 2. Design Direction

### A. Type Scale & Typography
The UI must feel like a precision instrument. We will use a geometric sans-serif paired with a monospace font for technical readouts.
- **Technical Metrics (e.g., ISO 400, f/2.8, ±2.0 EV):** 16pt, **Bold**, Monospace or tabular figures. High contrast (White).
- **Sub-labels (e.g., APERTURE, SHUTTER, STEP):** 10pt, *Medium*, All-Caps, high tracking (+4% letter spacing), muted opacity (60%).
- **Accent States:** The active state for highly technical modes (like the "BKT" button or selected EV step) should use a stark Gold/Yellow accent color to stand out against the dark panels.

### B. Spacing Rhythm & Layout
The layout avoids pinning heavy solid boxes to the edges of the screen unless in "Pro" modes.
- **Floating Panels (Standard Mode):** The main control panel floats near the bottom with rounded corners (e.g., 24px radius), allowing the camera feed to bleed around the edges.
- **Solid Bottom Sheets (Pro Mode):** When deep technical control is needed, a dark, opaque panel smoothly slides up to anchor the bottom edge, providing rigid, grid-based spacing for dials and thumbnails.
- **Grids & Brackets:** The Rule-of-Thirds grid lines must remain perfectly unpadded (edge-to-edge). Object scanning brackets should be heavily padded (e.g., 64px from the edges) to focus the user's attention on the center.

### C. Visual Hierarchy (Tactile vs Digital)
Hierarchy is established through **shape and contrast**:
1. **The Live Feed:** Takes up the entire fullscreen viewport, bleeding under all controls.
2. **The Coaching Tooltip:** The highest priority element when the user needs guidance. It uses a high-contrast white bubble or glassmorphism pill, placed asymmetrically to draw the eye.
3. **The Center Iconography:** When spatial mapping is required, a stark white, flat icon (like a phone panning) appears dead center.
4. **The Control Panel:** Isolates complex controls from the noisy camera feed using a dark blurred glassmorphism layer or a solid dark sheet.
5. **The Shutter Button:** Pure white, perfectly circular, with a distinct outer ring. (Avoid highly saturated color gradients for the shutter to maintain the professional aesthetic).

### D. State Signaling
State is signaled through tactile, physical metaphors and spatial AR cues:
- **Spatial AR Cues:** Using high-contrast yellow dots tracked to the physical floor/objects to indicate successful AR mapping.
- **Active State (Text):** Text turns into the primary accent color (Gold/Yellow) or transitions to full opacity bold.
- **Dial Feedback:** Curved dotted dials or circular rotation dials rotate to indicate state, replacing traditional linear sliders.
- **Toggle Switches:** Clean, rounded glassmorphism switches that physically slide to indicate state.
