# KoSpeaker

![CI](https://github.com/RazMagal/kospeaker/actions/workflows/ci.yml/badge.svg)

**A natural-sounding, fully offline system Text-to-Speech engine for Android — built to give KOReader on Onyx Boox e-ink devices a human-quality voice for Read Aloud of EPUBs.**

KoSpeaker registers itself as an Android system TTS engine and runs small neural voices (Piper by default) entirely on-device. Set it as your default TTS and every app that speaks — including KOReader's Read Aloud — instantly sounds natural, with no cloud, no account, and no network access after the one-time voice download.

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

After you download a voice once, no further network access is needed. Synthesis, text handling, and audio all happen locally on the device.

## Features

- **Neural offline voices via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).** Piper voices (VITS exported to ONNX) are the default: tiny (~30 MB), fast, and comfortably real-time on weak ARM CPUs — ideal for e-ink devices.
- **Kokoro-82M high-quality mode (planned).** An optional richer-prosody engine (~80 MB, heavier) for users who prioritize voice quality over speed. See the [roadmap](ROADMAP.md).
- **Reading pipeline built for prose.** Incoming EPUB text is normalized (whitespace, punctuation, abbreviations, and stray markup cleaned up) and split into sentence-sized chunks that are streamed to the audio output. Sentence chunking keeps latency low so playback starts quickly and page turns feel responsive on slow e-ink refresh.
- **Built-in voice downloader.** Install and manage Piper voices from the in-app *Manage Languages* screen; this is the only moment the app touches the network.
- **Standard system engine.** Works with KOReader, but also with any Android app that uses system TTS.

## Install

- **Build from source** — see [Build](#build) below. Recommended while KoSpeaker is under active development.
- **CI artifact** — each push produces a debug APK as a CI build artifact you can sideload.
- **F-Droid (upstream)** — KoSpeaker itself is not on F-Droid. Its upstream base, SherpaTTS, is available on [F-Droid](https://f-droid.org/packages/org.woheller69.ttsengine/) if you want to try the unmodified engine first.

## Quick start on Onyx Boox + KOReader

Full, step-by-step instructions live in **[docs/KOREADER_SETUP.md](docs/KOREADER_SETUP.md)**. In short:

1. Build or sideload the KoSpeaker APK and open it.
2. Download a Piper English voice from *Manage Languages*.
3. In Android **Settings → System → Languages & input → Text-to-speech output**, set **KoSpeaker** as the default engine and tune speech rate/pitch.
4. Install `audiobook.koplugin` into KOReader, open an EPUB, and start **Read Aloud**.

## Build

Requirements:

- JDK 17
- Android SDK (compileSdk 35; min SDK 29)

Common tasks:

```bash
# Build a debug APK (output under app/build/outputs/apk/)
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

The native neural runtime is pulled in as the `com.github.k2-fsa:sherpa-onnx` dependency, so no manual NDK setup is required for a standard build.

## Roadmap

Planned milestones — Kokoro-82M optional engine, e-ink-friendly UI, a one-tap "set as default + KOReader setup" helper, per-book presets, and CI release APKs — are tracked in **[ROADMAP.md](ROADMAP.md)**. The iterative, LLM-driven development methodology behind the project is described in **[LOOP.md](LOOP.md)**.

## Credits & License

KoSpeaker is a fork of **[woheller69/ttsEngine ("SherpaTTS")](https://github.com/woheller69/ttsEngine)** — GPLv3, © woheller69 — and builds on that project's proven e-ink support and built-in Piper voice downloader.

It stands on:

- **[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)** (k2-fsa) — Apache-2.0 — neural TTS runtime.
- **[Piper](https://github.com/rhasspy/piper)** / **[Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M)** — the neural voice models (Apache-2.0 class).
- **[eSpeak NG](https://github.com/espeak-ng/espeak-ng)** — GPLv3 — phonemization data.
- **[jsoup](https://github.com/jhy/jsoup)** — MIT — HTML/text handling.

KoSpeaker is licensed under **GPLv3** (inherited from the upstream fork). See [LICENSE](LICENSE) and [NOTICE](NOTICE) for full attribution.
