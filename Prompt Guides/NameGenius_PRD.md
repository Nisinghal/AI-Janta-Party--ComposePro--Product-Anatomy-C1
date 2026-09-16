# NameGenius — Product Requirements (Draft)

> Status: draft for review. Once the shape and voice are approved, this becomes the consolidated PRD, and each phase below is also cut into its own standalone file for the matching live session.

---

## What NameGenius is

NameGenius helps someone naming a company, product, or project find a name they can actually own. The user checks whether a name's domain is free, and when it isn't, the tool suggests alternatives that are both available and on-brand.

That second half is the point. Every domain registrar can tell you `acme.com` is taken. NameGenius reads what the business is about, from a description, keywords, or competitor names, and proposes available names that fit that brand, not random available strings.

**Target user:** anyone naming something, from an indie founder to a product team inside a large company. What unites them is the job, not the job title. They want to shop for and validate names quickly instead of checking domains one at a time.

## The jobs it does

1. "I have a name in mind. Is the domain free?"
2. "It's taken, or I just want options. Give me names that are available and fit my brand."

The tool always leans on the marketing angle. Suggestions have to reflect the description, keywords, and competitor context the user provides. Competitor names do double duty: they flavor suggestions and act as a "don't collide with these" constraint.

## Inputs

A user can start with any one of three inputs. Any single field is enough to run. All three together give the best results.

| Input | Role | Notes |
|---|---|---|
| Name | The subject to check | The thing the user wants a domain for. |
| Description | Brand flavor | Up to about 1,000 characters. |
| Competitors and keywords | Flavor plus a constraint | Shapes suggestions and flags collisions to avoid. |

### The brand-discovery questions

Beyond the three inputs, there is an optional layer of evocative questions that sharpen the output. The user answers any subset, at any time, and re-runs. One answer added later still improves the next batch. Nothing is required.

These answers feed the generation prompt as extra brand context, building into a brief the user grows over time. The current question bank:

- What feeling do you want to evoke in your audience?
- What are the main actions you want people to take?
- If your company were a rare plant or animal, which would it be?
- What analogies fit how your business operates?
- How would you explain your project to a five-year-old and keep them interested?
- Does this concept exist in other industries, and do they use different words for it?
- What are a few good metaphors for what you do?
- What role in people's lives are you trying to fill?

The questions are not a form the user must clear before starting. The tool runs on minimal input first, then invites enrichment after showing results.

## The core loop

1. User provides at least one input.
2. Tool generates 5 name candidates and checks domain availability for each.
3. User can regenerate for 5 more, or act on the ones shown.
4. After 3 generations with no selection, the tool surfaces one brand-discovery question to sharpen the next batch. This turns the question bank into an adaptive nudge triggered by an unhappy user, not just a passive side panel.

## Results

Each result is a name plus its domain status. Depending on how the user arrived, results fall into three related shapes:

- A plain availability check when only a name is entered.
- Available, on-brand alternatives generated from the description, keywords, competitors, and the current name.
- The same alternatives, surfaced because the user's chosen name was taken.

The last two produce the same kind of output. The data model treats it as one "alternatives" list that appears whenever there is enough brand context, however it was triggered.

### Actions on a result

Two actions ride on each result. We are not fully designing them yet, only reserving them:

- **Shortlist:** save names the user likes into one place to review together.
- **Add to compare:** pull selected names into a side-by-side view.

## TLD handling

The user does not filter a finished list by TLD. Instead, TLD preference is an input to generation. The tool defaults to prioritizing `.com`. The user can optionally say to prioritize others, for example `.io` or `.ai`, and generation biases toward names whose preferred TLD is free before falling back to the rest.

Each result carries which TLDs are available, and the preferred TLD drives ranking.

## Feature backlog

Beyond the core loop, these are candidate features, listed roughly by build size. They get slotted into the teaching phases below.

1. Copy a name to clipboard (tiny)
2. Shortlist a name, stored locally (small)
3. TLD filter, for example `.com` or `.io` (small)
4. Name-length filter (small)
5. Add-to-compare view (medium)
6. Price display and a price filter, for example "available for free" or "under $X" (medium, depends on the API)
7. TLD prioritization inside generation (larger)
8. The adaptive brand-question refine loop (larger)

## Constraints and out of scope

- No database and no accounts. Shortlist, compare, and history all live in the browser via localStorage or caching.
- No auth, no deployment to servers, no cloud storage. This matches the track's stated boundaries.
- Price is a nice-to-have, not a day-one requirement. Availability is the must-have (see below).

## Technical direction

Two live APIs give the product its spine: one for generation, one for domain checks. Recommendations below, marked for you to confirm.

**Domain availability (must-have).** Recommend RDAP, the standard registration lookup protocol. It is free, keyless, and returns a clear registered-or-not signal, so it is easy to run in a live class. This is the low-friction API.

**Domain price (out of core).** Price usually comes from a registrar API that needs a key, and premium pricing varies a lot. Decision: keep price out of the core track. It appears only as an optional stretch in Phase 5 for a fast group, never as a required build.

**Generation (decided).** Google Gemini API, model Gemini 2.5 Flash, on the free tier. Each student creates their own free key at https://aistudio.google.com/apikey. It is the one strong LLM API whose free tier needs no credit card, so a room of designers can each get a key in about a minute, and it still uses a real key, which LS3's secrets lesson needs. A shared instructor key is the fallback for anyone who stalls. Anthropic and OpenAI both require billing to start, so they are documented as swap-in alternatives, not the default.

- Docs: https://ai.google.dev/gemini-api/docs
- Get a key: https://aistudio.google.com/apikey

**Project stack (decided).** Vite + React + Tailwind CSS, written in JavaScript rather than TypeScript to keep friction low for non-coders. Adjustable if a cohort is more technical.

## Design direction

Bold, playful, and confident, carried by typography and layout rather than color and imagery. Monochrome to start, black and white, with topographic design elements. This is a starting point. The Mobbin reference chosen in Phase 1, and the system it grows into across Phase 2, can shift it.

## Screen architecture

The product is not a single screen. It breaks into a small set of views:

### Screens

| ID | Screen | Contents |
|---|---|---|
| S1 | Brief (input) | Name field, description textarea (about 1,000 chars), competitors-and-keywords field, TLD preference control, primary "Find names" action, and an entry point to the brand-discovery questions. |
| S2 | Results | The 5 result cards, a regenerate control, result filters (TLD, name length), and the adaptive question nudge area. |
| S3 | Result card (component) | Name, domain, availability status badge, available-TLD indicators, and the per-card actions: copy, shortlist toggle, add to compare. |
| S4 | Shortlist | Saved names in one place, remove a name, copy the list. Stored in localStorage. |
| S5 | Compare | A side-by-side view of selected names across name, domain status, available TLDs, and why each fits. |
| S6 | Brand-discovery questions panel | The 8 optional questions, answered in any subset, saved, and used to sharpen the next generation. |

### Flows

| ID | Flow | Path |
|---|---|---|
| F1 | Quick check | Enter a name only, see availability. |
| F2 | Generate | Enter description or keywords with no name, get 5 available on-brand alternatives. |
| F3 | Taken name | Enter a name that is taken, see it flagged, then see alternatives. |
| F4 | Regenerate | Not satisfied, regenerate 5 more; after 3 straight regenerations a brand question appears. |
| F5 | Enrich | Open the questions panel, answer some, re-run, get sharper results. |
| F6 | Shortlist | Like a name, shortlist it, view the shortlist. |
| F7 | Compare | Add two or more names to compare, view side by side. |
| F8 | Filter | Apply TLD or name-length filters to the current results. |

### Cases and states to handle

Empty (no input yet, or no results). Loading (generating, checking domains). Error (API failure or rate limit). Available versus taken per domain. Partial input (only one field filled). Three straight regenerations with no new search in between (triggers a question). localStorage empty versus populated (shortlist and compare, in the Phase 4 practice repo).

---

## The six phases

Each phase maps to one live session and its skill. The build grows across phases; the group project applies the same skill in parallel. One component is designed with taste in Phase 1, the full flow is designed and built to its happy path in Phase 2, and each phase from Phase 3 on adds one new layer — live data and the states it needs, then small features in an unfamiliar codebase, then a larger feature scoped and shipped agentically.

| Phase | Session | NameGenius reaches | Skill taught |
|---|---|---|---|
| 1 | LS1 | One component designed with taste: the result card, in Figma or Paper, anchored by a real Mobbin reference | Directing an AI design tool with a reference, the Describe/Review/Iterate loop |
| 2 | LS2 | Brief and Results designed and built as a real, running app — inputs, five result cards, regenerate — happy path only, no edge states | The five-part prompt framework, plan vs. execution mode, Figma MCP across a connected flow, designing with a system |
| 3 | LS3 | The core loop live (generation, domain checks, regenerate, copy), plus the empty, loading, and error states Phase 2 deliberately deferred | Live API integration, designing a state directly in code, keys and secrets |
| 4 | LS4 | Shortlist and compare added to a provided practice repo, each designed and built end-to-end by its own agent in its own git worktree, in parallel | Working in an unfamiliar codebase, parallel agentic builds via git worktrees, clean PRs |
| 5 | LS5 | A larger feature, designed and wired agentically in one pass — the questions panel's adaptive refine loop, or the result filters | Scoping small, an agentic coding session, the full loop |
| 6 | LS6 | Packaged as a portfolio case study | Positioning and narrative |

### Feature distribution across the phases

Every feature from the backlog has a home. Design places the affordance, "built" gives it its happy-path shape with mocked data, and "wired" is where it runs on real logic or live data. Most features now pass through all three stages, each in a different phase — that's deliberate: it's what keeps any one session from carrying both a design load and a build load at once.

| Feature | Designed | Built (happy path) | Wired (real) |
|---|---|---|---|
| Result card (S3): anatomy, Available/Taken/loading states | Phase 1 | Phase 2 (Available/Taken only) | Phase 3 (adds loading, live data) |
| Brief screen (S1), inputs | Phase 2 | Phase 2 | Phase 3 |
| Generation, 5 candidates | Phase 2 | Phase 2 (mocked) | Phase 3 |
| Domain availability check | Phase 2 | Phase 2 (mocked) | Phase 3 |
| Three result states (F1, F2, F3) | Phase 2 | Phase 2 | Phase 3 |
| Results-area empty, loading, error states | Phase 3 (deferred from Phase 2) | Phase 3 | Phase 3 |
| Regenerate for 5 more | Phase 2 | Phase 2 (inert) | Phase 3 |
| Copy a name to clipboard | Phase 2 | Phase 2 (inert) | Phase 3 |
| Shortlist a name, shortlist screen (S4) | Phase 4 — practice repo | Phase 4 — practice repo | Phase 4 — practice repo, one of two features built in parallel, each by its own agent in its own git worktree (see open item below) |
| Add-to-compare view (S5, F7) | Phase 4 — practice repo | Phase 4 — practice repo | Phase 4 — practice repo, the other of the two parallel-agent features |
| Brand-discovery questions panel (S6) | Phase 5 | Phase 5 | Phase 5, in the student's own repo (anchor) — nothing about it exists before this phase |
| Adaptive refine loop (F4, F5) | — | — | Phase 5 (anchor) |
| TLD filter, `.com`/`.io` (F8) | Phase 5 | Phase 5 | Phase 5 (alternate) — nothing about it exists before this phase |
| Name-length filter (F8) | Phase 5 | Phase 5 | Phase 5 (alternate) |
| TLD prioritization in generation | — (pure logic, no UI) | — | Phase 5 (alternate) |
| Price display and filter | not designed anywhere | Phase 5 optional stretch only | Phase 5 optional stretch only |

**Open item:** shortlist and compare now live only in the Phase 4 practice repo — the student's own NameGenius never gets either feature built in. Phase 6's case study should present them as a skill demonstrated (reading an unfamiliar codebase, shipping via parallel agents and clean PRs), not as part of "what shipped" in the personal product. "What shipped" in the student's own NameGenius is the brief-to-results loop, live generation and domain checks, and whichever Phase 5 anchor/alternate (the adaptive question, or the filters) that student's group built.

### Phase 1 (LS1) — One component, designed with taste

**Goal.** A confidence-first opener that leads with design taste, not code. Direct an AI design tool, anchored by a real reference, to build one small, well-crafted part of the product.

**Build requirements.**
- One component only: the result card (S3), full anatomy — name, domain, availability badge, available-TLD indicators, and the three actions (copy, shortlist, add to compare).
- Three states of that one card: Available, Taken, and loading.
- No other screen exists yet, not even as a stub. No generation, no API, no storage, no code at all — this phase produces a Figma or Paper file, built via its MCP server.

**Design requirements.**
- Establish the monochrome, typographic direction. Bold type, strong layout, black and white.
- Anchor the design in a real reference pulled from Mobbin, rather than a blank page.

**Outcome.** A student sees a real, tasteful component take shape in minutes from a short, reference-anchored prompt, and leaves with the visual direction set.

### Phase 2 (LS2) — Design and build the happy flow

**Goal.** Extend the Phase 1 card into a small design system, design the Brief and Results screens in Figma, then convert that flow to real, running code.

**Build requirements.**
- Design S1 Brief and S2 Results in Figma, sharing one type scale, spacing system, and monochrome palette seeded from the Phase 1 card. S3, the result card, is reused as-is, carrying only its copy action — nothing else is built or even stubbed this phase.
- Convert the flow to a real, styled, navigable app via MCP: typing into the brief updates real state, "Find names" produces five mocked candidates on Results, and regenerate swaps in a new batch.
- Happy path only: every screen shows its ideal, populated state with mocked data. No empty, loading, error, or validation states this phase — those are deliberately deferred, phase by phase, to wherever there's live logic to justify them.
- Shortlist, compare, the result filters, and the questions panel are not part of this phase, not even as stubs. Shortlist and compare are built end-to-end in Phase 4, in the practice repo; the filters and the questions panel are designed, built, and wired in Phase 5.
- First use of git: initialize a local repo, commit three times as the app takes shape, then revert to the second commit.

**Design requirements.**
- One coherent design system across the two screens, not two separate looks.
- Use the session's anti-slop techniques so the output looks intentional, not generic.

**Outcome.** A student grows one component from Phase 1 into a small, running product with a real interaction loop, and gets their first experience of git as a safety net.

### Phase 3 (LS3) — Live generation and domain checks, plus the states Phase 2 deferred

**Goal.** The core loop works end to end for the first time, and the results area finally gets the empty, loading, and error states Phase 2 deliberately skipped.

**Build sketch.**
- Wire generation to Gemini and availability to RDAP.
- Build flows F1, F2, F3: take the inputs, produce 5 candidates, check each domain, render availability, and cover all three result states.
- Design and build the empty, loading, and error states for the results area directly in code, using the tokens set in Phase 2 — no return trip to Figma needed for states this small.
- Apply the result card's loading variant, already designed back in Phase 1, wherever a domain check is in flight.
- Regenerate for 5 more (part of F4). Copy a name to clipboard.
- Introduce keys and secrets: the `.env` file, and never committing it.

**Outcome.** A working NameGenius that generates on-brand names, checks domains live, handles its states honestly, and lets the user copy a name — plus the student's first push to GitHub.

### Phase 4 (LS4) — Two features, two agents, one practice repo

**Goal.** Add shortlist and compare, end to end, to a provided practice repo — a separately built NameGenius implementation with its own conventions, not the student's own project — using parallel agents in separate git worktrees, without breaking it, each shipped as its own clean PR.

**Build sketch.**
- Read the practice repo first and confirm shortlist (a toggle on the result card, a shortlist screen, localStorage persistence — S4, F6) and compare (an add-to-compare action, a side-by-side compare screen — S5, F7) don't touch any of the same files.
- Set up one git worktree per feature off that repo, and hand each feature to its own agent, working at the same time, matching that repo's existing conventions.
- Review each diff once both land, then ship each as its own branch and PR back to the practice repo's main.
- The lesson holds each PR to one purpose: what, why, a screenshot, and how to test it.

**Outcome.** A student reads a codebase they didn't write, builds a real feature end to end inside it, and gets a first, guided taste of running two agents in parallel — the technique Phase 5 asks them to run unaided. *(See the open item under "Feature distribution" above — the student's own NameGenius isn't touched this phase.)*

### Phase 5 (LS5) — A larger feature, wired agentically and scoped down

**Goal.** Independently direct an agentic coding session to wire a bigger feature onto UI that already exists, after watching the instructor cut a too-large idea down to a shippable slice.

**The agentic coding session.** This is the phase where students stop steering line by line and instead write one strong plan, hand a well-scoped feature to Claude Code, let it execute, then review the diff and correct. It is the full loop run at arm's length.

**Build sketch.**
- Anchor feature: the questions panel (S6) — nothing about it exists before this phase, so it's designed directly in code, built, and wired in one pass: the save-and-feed logic, then the adaptive refine loop (F4, F5). The full loop is big, so the taught slice is "surface one question after 3 straight regenerations."
- Alternate: the TLD and name-length filters (F8) — also nothing about them exists yet in the student's own repo, so this phase designs, builds, and wires them directly on the results screen.
- Second alternate: TLD prioritization inside generation — pure logic, no UI to wire.
- Optional stretch for a fast group only: price display and a price filter — unlike the anchor and alternate, this was never designed anywhere, so it needs a screen built from scratch. It also needs a keyed registrar API and stays out of the core requirement.
- The scoping cut is the lesson: name the tempting big version, then ship the smallest slice.

**Outcome.** A student scopes their own feature down, runs an agentic session to wire it onto existing UI, reviews the result, and ships the full git cycle unaided.

### Phase 6 (LS6) — Packaging and positioning

**Goal.** No new build. Turn the work from Phases 2 through 5 into a portfolio case study.

**Requirements.** The problem, the key decision, what shipped, and what the student would do next. A decision log and a note on impact. "What shipped" is the student's own NameGenius: brief to results, live generation and domain checks, and whichever Phase 5 anchor or alternate that group built. Shortlist and compare, built in Phase 4's practice repo, are described as a skill demonstrated — reading an unfamiliar codebase, shipping via parallel agents — not as shipped features of the personal product.

**Outcome.** NameGenius becomes a story a student can tell in an interview.

---

## Decisions on record

1. Generation API: Google Gemini 2.5 Flash, free tier, each student's own key, shared instructor key as fallback.
2. Price: out of the core track, optional Phase 5 stretch only.
3. Phase 4 features: shortlist and compare, each built end-to-end by its own agent in its own git worktree, in a provided practice repo. Phase 5 anchor feature: the adaptive brand-question refine loop, scoped down and designed, built, and wired from scratch; the TLD/length filters (or TLD prioritization) is the alternative.
4. Stack: Vite + React + Tailwind CSS, JavaScript.
5. Domain availability API: RDAP, keyless.
6. Phase 1 build surface: Figma or Paper, directed via its MCP server and anchored by a Mobbin reference — not Claude Code and Vite as in the original draft. No code is written until Phase 2.
7. Teaching model: happy path first. Phase 2 builds its two screens to their ideal, populated state only; empty, loading, error, and other edge states are deliberately deferred and designed later, phase by phase, right when there's live data or logic to justify them (Phase 3 for the results area, Phase 4 for shortlist's and compare's own states, Phase 5 for the questions panel and the result filters).
8. Phase 4 runs in a separate, instructor-provided practice repo rather than the student's own NameGenius, to keep the unfamiliar-codebase exercise genuine now that the student's own repo carries real investment by that point, and to give two independent, non-overlapping features (shortlist, compare) clean ground for a first parallel-agent, git-worktree exercise. **Resolved:** the student's own NameGenius never gets shortlist or compare built in — Phase 6's case study covers them as a skill demonstrated, not a shipped feature (see the open item under "Feature distribution across the phases" above).
