# KoSpeaker

![CI](https://github.com/RazMagal/kospeaker/actions/workflows/ci.yml/badge.svg)

**A fully offline, natural-sounding Android system Text-to-Speech engine — built to give [KOReader](https://koreader.rocks/) on Onyx Boox e-ink devices a human-quality voice for Read Aloud of EPUBs.**

KoSpeaker registers itself as an Android **system TTS engine** and runs small neural voices entirely on-device via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Set it as your device default and every app that speaks — including KOReader's Read Aloud — instantly sounds natural, with no cloud, no account, and no network access after the one-time voice download.

> KoSpeaker is a fork of [woheller69/ttsEngine](https://github.com/woheller69/ttsEngine) ("SherpaTTS", GPLv3). The original engineering here is the **reading pipeline**, the **offline Hebrew** support, and the **guided KOReader onboarding** — see [Architecture](#architecture).

---

## Why

E-ink readers like the Onyx Boox are wonderful for long-form reading, but their stock offline voices are non-neural (eSpeak / RHVoice-class). They work, but they sound flat and robotic — tiring over a full book. The obvious alternatives are cloud TTS engines, which defeat the point of an offline reader, drain battery, and raise privacy concerns.

KoSpeaker fixes the root cause: it replaces the robotic offline voice with a **small neural speaking model** that runs in real time on modest ARM hardware, so Read Aloud sounds like a person — and stays 100% offline.

## How it works

KOReader on Android has no speech synthesis of its own. Its Read Aloud plugin (`audiobook.koplugin`) wraps Android's system `android.speech.tts.TextToSpeech` API and speaks using whichever engine is set as the device **default TTS**.

KoSpeaker plugs into that exact seam:

1. It declares an `android.intent.action.TTS_SERVICE`, so Android lists it as an available TTS engine.
2. You select KoSpeaker as the **default** engine in Android settings.
3. From then on KOReader — and any other app — synthesizes speech through KoSpeaker.

After you download (or sideload) a voice once, no further network access is needed. Text handling, synthesis, and audio all happen locally on the device.

## Features

### Voices (on-device, via sherpa-onnx v1.13.0)

| Engine | Role | Size | Notes |
| --- | --- | --- | --- |
| **Piper** (VITS→ONNX) | **Default** | ~30 MB | Fast and comfortably real-time on weak ARM CPUs — the right default for e-ink. Installed from the in-app voice downloader. |
| **Kokoro-82M** | Optional high-quality mode | ~80 MB | Richer prosody for users who prioritize voice quality over speed. Selectable per model type; not forced on low-end hardware. |
| **Meta MMS-heb** | Offline **Hebrew** | ~145 MB | Self-contained VITS with a raw-character frontend (no espeak / niqqud / lexicon). Sideloaded via a one-shot converter — see [Hebrew](#hebrew). |

### Reading pipeline (original work — pure Kotlin, unit-tested)

Prose-oriented text handling that lives in the `reading/` package and has **no Android dependencies**, so it is exercised by fast JVM unit tests:

- **Text normalization** — collapses whitespace, strips zero-width/soft-hyphen characters, straightens smart quotes, normalizes ellipses, turns spaced em/en dashes into comma pauses, expands common abbreviations, and cleans up footnote markers and bracketed `[12]` citations. ([`TextNormalizer.kt`](app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/reading/TextNormalizer.kt))
- **Sentence-chunked streaming** — splits an utterance into sentence-sized chunks (abbreviation-, decimal- and initial-aware) so playback starts fast (low time-to-first-word) and a **Stop** request interrupts cleanly between chunks instead of waiting out a whole paragraph — important on slow e-ink CPUs. ([`SentenceChunker.kt`](app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/reading/SentenceChunker.kt))
- **Script detection** — classifies text as Latin / Hebrew / Mixed / Other so the pipeline can adapt per script, including Hebrew punctuation and RTL handling. ([`TextScript.kt`](app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/reading/TextScript.kt))
- **Number & year verbalization for English *and* Hebrew** — spells digits out as words (e.g. `1996` → "nineteen ninety-six"), including years, ordinals, currency and percentages. Hebrew numbers are spelled out too, because the MMS voice's vocabulary has **no digits** and would otherwise drop them. ([`NumberVerbalizer.kt`](app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/reading/NumberVerbalizer.kt))

### UX

- **Guided "Set up with KOReader" onboarding** — a self-contained screen that opens the system TTS settings (to pick KoSpeaker as default and tune speed/pitch), shows a **live "is KoSpeaker the default engine?" status**, walks through the KOReader Read Aloud steps, reminds you to **keep** `audiobook.koplugin` (KoSpeaker is the voice it drives, not a replacement), and includes an e-ink page-turn tip. ([`SetupActivity.kt`](app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/SetupActivity.kt))
- **In-app voice downloader / Manage Languages** — install and manage Piper (and Kokoro) voices; this is the only moment the app touches the network.
- **Standard system engine** — works with KOReader, but also with any Android app that uses system TTS.

> **Scope:** KoSpeaker targets **EPUB** reading in KOReader. PDF is not supported yet.

## Hebrew

Offline Hebrew is a standout feature. KoSpeaker reads Hebrew EPUBs aloud fully offline using Meta's self-contained [`facebook/mms-tts-heb`](https://huggingface.co/facebook/mms-tts-heb) VITS voice. Because there is no pre-built sherpa-onnx download for Hebrew, the repo ships a one-shot converter — [`scripts/convert_mms_hebrew.py`](scripts/convert_mms_hebrew.py) — that produces `model.onnx` + `tokens.txt` to sideload once. The engine loads it via a `vits-mms` model type (empty dataDir/lexicon → character frontend) that you pick in the **Install from SD** dialog.

The reading pipeline is Hebrew-aware (script detection, Hebrew punctuation, and the number spelling-out described above), which is what keeps digits and years from being silently dropped by a voice whose vocabulary has none.

**Quality expectation:** MMS is 16 kHz — intelligible but flat, and because everyday Hebrew is written without niqqud it will make occasional homograph errors. It is genuinely usable for reading today; a markedly more natural route (phonikud → 22 kHz Roboshaul) is on the [roadmap](#roadmap).

Full end-to-end guide: **[docs/HEBREW.md](docs/HEBREW.md)**.

## Install & build

Requirements:

- **JDK 17**
- **Android SDK** (`compileSdk 35`, `minSdk 29`)

Common tasks:

```bash
# Build a debug APK (output under app/build/outputs/apk/debug/)
./gradlew assembleDebug

# Run the unit tests
./gradlew testDebugUnitTest
```

The native neural runtime is pulled in as the `com.github.k2-fsa:sherpa-onnx:v1.13.0` dependency, so no manual NDK setup is required for a standard build. Each push also produces a debug APK as a CI artifact you can sideload.

## Quick start on Onyx Boox + KOReader

Full, step-by-step instructions live in **[docs/KOREADER_SETUP.md](docs/KOREADER_SETUP.md)**. In short:

1. Build or sideload the KoSpeaker APK and open it.
2. Download a Piper English voice from **Manage Languages** (or sideload a Hebrew voice — see [docs/HEBREW.md](docs/HEBREW.md)).
3. In Android **Settings → System → Languages & input → Text-to-speech output**, set **KoSpeaker** as the default engine and tune speech rate / pitch. The in-app **Set up with KOReader** screen can open this for you and confirm KoSpeaker is the default.
4. Install `audiobook.koplugin` into KOReader, open an EPUB, and start **Read Aloud**.

## Architecture

- The reasoning behind forking SherpaTTS and the Piper-default / Kokoro-optional split is captured in [ADR 0001 — Architecture and base engine choice](docs/adr/0001-architecture.md).
- The planned premium-Hebrew path (on-device niqqud diacritizer → IPA → 22 kHz Roboshaul VITS) is captured in [ADR 0002 — Premium Hebrew TTS via phonikud](docs/adr/0002-phonikud-hebrew-tts.md).

The base fork provides the proven pieces — system-TTS-engine registration, model management, and the built-in Piper voice downloader on top of **sherpa-onnx**. KoSpeaker's *original* value sits as a distinct, reviewable layer: the pure-Kotlin **`reading/`** package (normalization, chunking, script detection, number verbalization), the offline Hebrew support, and the KOReader onboarding screen.

### Quality & engineering

- **70 unit tests** (JUnit) covering the reading pipeline.
- **GitHub Actions CI** — runs the tests (must-pass gate), lint, and `assembleDebug`, and uploads the debug APK as an artifact. See [`.github/workflows/ci.yml`](.github/workflows/ci.yml).
- Builds locally with the Android SDK; the neural runtime is a Gradle dependency (no manual NDK setup).

## Roadmap

Milestones — the Kokoro high-quality mode, premium Hebrew via **phonikud**, an e-ink-friendly UI, per-book voice/speed presets, and signed CI release APKs — are tracked in **[ROADMAP.md](ROADMAP.md)**. The iterative, LLM-driven development methodology behind the project is described in **[LOOP.md](LOOP.md)**.

## Credits & License

KoSpeaker is a fork of **[woheller69/ttsEngine](https://github.com/woheller69/ttsEngine)** ("SherpaTTS") — **GPLv3, © woheller69** — and builds on that project's proven Onyx Boox / e-ink support and built-in Piper voice downloader.

It stands on:

- **[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)** (k2-fsa) — Apache-2.0 — neural TTS runtime.
- **[eSpeak NG](https://github.com/espeak-ng/espeak-ng)** — GPLv3 — phonemization data.
- **[jsoup](https://github.com/jhy/jsoup)** — MIT — HTML/text handling.

**Voice models** — [Piper](https://github.com/rhasspy/piper), [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M), [MMS-heb](https://huggingface.co/facebook/mms-tts-heb), and (planned) phonikud/Roboshaul — carry **their own licenses** (MMS is **CC-BY-NC 4.0**, non-commercial). They are **downloaded or sideloaded, not bundled** with the app.

KoSpeaker itself is licensed under **GPLv3** (inherited from the upstream fork). See [LICENSE](LICENSE) and [NOTICE](NOTICE) for full attribution.
