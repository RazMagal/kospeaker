# The Development Loop

KoSpeaker is built through a disciplined, LLM-driven agent loop. Rather than one large rewrite, the project advances one roadmap item at a time, each in a small, testable, reviewable change set. This document describes that methodology — it is the engineering process, not a to-do list.

## One iteration

Every iteration follows the same cycle:

1. **Pick the next item.** Take the top unchecked milestone from [ROADMAP.md](ROADMAP.md). Exactly one item is in flight at a time, so scope stays small and intent stays clear.
2. **Implement in an isolated change set.** Make the change as a focused, self-contained diff. Keep it narrow: touch only what the milestone needs, so it can be understood and reviewed on its own.
3. **Keep tests green in CI.** Add unit tests for new logic (text normalization, sentence chunking, and other pure functions are the priority targets) and make sure the existing suite still passes. `./gradlew testDebugUnitTest` must be green locally and in CI before the change is considered done.
4. **Commit.** Land the change as a coherent commit with a message that states which roadmap item it advances.
5. **Review.** Read the diff critically for correctness, scope creep, and clarity. On-device behavior (Read Aloud in KOReader) is spot-checked when the change affects the user-facing reading experience.
6. **Repeat.** Check the item off the roadmap and start the next iteration.

## Why this shape

- **Small change sets** keep each step reviewable and easy to reason about — and easy to revert if an iteration goes wrong.
- **Tests-first-where-it-counts** turns the reading pipeline (normalization, chunking) into regression-proof, self-documenting logic rather than untested guesswork.
- **CI as the gate** means "done" has an objective, repeatable definition instead of a subjective one.
- **On-device verification** guards against the gap between "compiles and passes tests" and "actually sounds right on an e-ink Boox."

## Exit criteria for the loop

The loop stops when:

- The **core roadmap items** are complete — the Piper-default neural voice, the reading pipeline, and the KOReader onboarding path.
- **CI is green** — build plus unit tests pass on every push.
- **KOReader is verified on-device** — Read Aloud of an EPUB works end-to-end on an Onyx Boox with a natural offline voice.

Once those hold, further roadmap items are optional enhancements run through the same loop, on the same terms.
