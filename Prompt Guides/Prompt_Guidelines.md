# Prompt guidelines for building with AI

For designers directing an AI tool to make a design and turn it into code. This is not a script to copy. It is a way of thinking about what you ask for and how you judge what comes back.

## The one idea to hold onto

Newer models do what you say, not what you meant. Ask for something vague and you get something generic, the look people call "AI slop." Tell the model exactly what good looks like and how you will check it, and you stay in control. A weak result is usually a weak prompt, so read the output as feedback on what you asked for.

## Do

- Decide what "good" looks like, and how you will check it, before you generate anything.
- Give the model something real to work from: a Figma frame, a screenshot, or a link. Without a reference it guesses, and it guesses generic.
- Open with one confident sentence naming the screen and the design language. Then add three to six hard limits, where each limit takes away a decision the model would otherwise make on its own: which sections, which spacing and type, which states, which platform, what to avoid.
- Build one screen or component at a time.
- Iterate by handing the result back: "here is the render, compare it to the reference, list what is different, fix it."
- Plan before you build anything bigger than a one-line change. For a tiny fix, skip the plan.
- Cut any line that does not change the output. Longer prompts are not better prompts.
- Fix course early. If you have corrected the same thing twice and it is still wrong, clear the chat and write a sharper prompt with what you learned.

## Don't

- Don't say "make it look good" or "make it modern." There is no bar in that, so you get slop.
- Don't ask for the whole interface in one go.
- Don't assume the model knows your design system. Name the tokens, or point it at a component you already have.
- Don't skip the reference. You and the model both need something concrete to measure against.
- Don't let the thing that built the design be the only thing that judges it. You check it against the reference, or you start a fresh review.
- Don't pile on more examples and rules hoping it helps. Past a point it makes the output worse.
- Don't keep correcting inside a messy thread. Start clean.
- Don't copy a reference's brand or logo. Take the structure and rhythm, not the identity.

## The framework: set a bar, then review against it

This is the part that turns a prompt into a skill. Two steps. Set the bar before you generate. Run a short review after.

### Before you generate, set the bar

Four things. If you cannot answer one of them, that is the part of your prompt that will fail.

1. A one-sentence default: the screen and its design language, said plainly.
2. A reference you can actually look at: a frame, a screenshot, or a URL.
3. Three to six hard limits, each removing a decision: sections, tokens, states, platform, and one or two things to avoid.
4. A way to check "done": for example, screenshot the result and compare it to the reference.

### After you generate, run three questions

Look at what came back and ask three things in order. Each "no" points at the exact fix for your next prompt.

1. Does it match the reference in spacing, type, and hierarchy? If no, hand the render back and name the specific differences.
2. Is it complete, with hover, disabled, empty, and responsive states? If a state is missing, you left it out of your limits, so add it.
3. Does it look intentional, or does it look generic? If it looks generic, your opening sentence or your reference was too weak. Make one of them sharper.

The habit to build: a bad output is a diagnosis of a bad input. Do not just run it again. Work out which of the three failed, and fix that part of what you asked for.

### Where this comes from

Two ideas from people who do this at scale.

Boris Cherny, who built Claude Code, describes his job now as writing loops rather than prompts. The practical version for you: give the model a way to check its own work, like a screenshot against the design, and it will keep fixing until it passes instead of stopping the moment it "looks done."

Matt Shumer's gauntlet loop showed that a short prompt can beat a long one, as long as it sets a real bar the model can inspect ("build this at the level of that") and keeps a separate, harsh check on the result. You will not run multi-agent loops in this course, but the principle is the same one above: pick a bar you can point at, and do not let the builder be the only judge.
