# Contributing to KoSpeaker

Thanks for your interest in KoSpeaker — an offline neural TTS engine for KOReader Read Aloud on e-ink devices. This guide covers how to build, test, and contribute.

## Prerequisites

- **JDK 17**
- **Android SDK** (compileSdk 35; minimum supported device SDK is 29)
- Git

## Build

```bash
# Debug APK -> app/build/outputs/apk/debug/
./gradlew assembleDebug
```

The neural runtime (`com.github.k2-fsa:sherpa-onnx`) is resolved as a dependency, so a standard build needs no manual NDK setup.

## Run tests

```bash
./gradlew testDebugUnitTest
```

Please keep the suite green. New logic should come with tests — the pure, deterministic parts of the reading pipeline (text normalization, sentence chunking) are the highest-value things to cover and the easiest to test.

## Coding style

- **Kotlin:** the official Kotlin style (`kotlin.code.style=official`, already set in `gradle.properties`). Prefer small, pure functions for text/prose logic so they stay unit-testable.
- **Java:** match the existing style of the surrounding file; new feature code is preferably Kotlin.
- Keep changes **focused**: one roadmap item per change set. Don't mix refactors with features.
- Preserve attribution and license headers. KoSpeaker is GPLv3 (see [NOTICE](NOTICE)); contributions are accepted under the same license.

## The loop workflow

KoSpeaker is developed through the iterative loop described in [LOOP.md](LOOP.md). In practice, a contribution should:

1. Pick the next open item from [ROADMAP.md](ROADMAP.md) (or open an issue to propose a new one).
2. Implement it as one isolated, self-contained change set.
3. Add/keep unit tests green (`./gradlew testDebugUnitTest`) so CI stays green.
4. Commit with a message stating which roadmap item the change advances.
5. Open a pull request; the diff is reviewed for correctness, scope, and clarity. Changes that affect the reading experience should be spot-checked on-device (KOReader Read Aloud on an e-ink device) where possible.

## Reporting issues

When filing a bug, include your device model and Android version, the KoSpeaker version, the voice/model in use, and — for Read Aloud problems — your KOReader version and whether KoSpeaker is set as the default system TTS engine.
