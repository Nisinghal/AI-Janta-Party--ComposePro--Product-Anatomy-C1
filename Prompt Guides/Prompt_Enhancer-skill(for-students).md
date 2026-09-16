---
name: prompt-enhancer
description: Use whenever the task is designing a UI, building a component, screen, or flow, or turning a Figma frame or screenshot into code. Forces the request to be defined (Bar, Reference, Limits, Check) before any code is written.
---

# Prompt Enhancer

Use this whenever the task is designing a UI, building a component, screen, or flow, or turning a Figma frame or screenshot into code. It makes you define the request before any code is written.

## Steps

1. Do not write code yet. Enter plan mode (read-only).
2. Check that these four are defined. Ask about only the parts that are actually missing — if the prompt already gives half of an element (e.g. the screen name but not its design language), ask just for the missing half:
   - Bar: one sentence naming the screen and its design language.
   - Reference: ask for the artifact directly ("paste a Figma link, drop a screenshot, or say none to design from scratch") — don't ask a yes/no meta-question about whether one exists first, that just costs a round trip.
   - Limits: three to six hard constraints. Walk through each sub-item — sections, tokens/spacing/type, states, platform, things to avoid — and ask about whichever aren't already known, batched into one question where possible, instead of picking one or two ad hoc. A reference shows structure and rhythm, not tokens — never infer spacing, type, or design-system values from it. Ask the student to name the tokens directly, or point at a component they already have.
   - Check: how "done" is verified, for example screenshot the result and compare it to the reference.
3. Write the brief back in four short lines (Bar, Reference, Limits, Check) and output it for the student to review. Wait for explicit approval — do not start building on an assumed yes.
4. After approval, leave plan mode and build one screen or component only.
5. After building, run the review: does it match the reference, is it complete across states and responsive sizes, does it look intentional? Report the differences and fix them against the reference.

## Notes

- This is written for Claude Code, where a skill can import this file. In a free tool without skills, use the same four-line brief as a checklist pasted before each build.
- Keep the brief short. If a line does not change the output, cut it.
