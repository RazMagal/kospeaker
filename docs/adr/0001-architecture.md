# ADR 0001: Architecture and base engine choice

- **Status:** Accepted
- **Date:** 2026-07

## Context

The goal of KoSpeaker is to give KOReader on Onyx Boox e-ink devices a natural-sounding, fully offline voice for Read Aloud of EPUBs. The constraints that shape every decision:

- **Offline-first.** An e-ink reader's whole value is calm, disconnected, long-form reading. Cloud TTS is out.
- **Runs on weak ARM.** Boox devices are power-efficient, not powerful. Synthesis must be real-time on modest CPUs.
- **Registers as a system TTS engine.** KOReader has no synthesizer of its own; its Read Aloud plugin uses Android's system `TextToSpeech` with the device default engine. To reach KOReader (and every other app) we must be a selectable system TTS engine, not a standalone reader.
- **Quality over the status quo.** The stock Boox offline voice is non-neural (eSpeak/RHVoice-class) and robotic. We need small *neural* voices.
- **Showcase quality.** This repository is also a portfolio piece, so the architecture should make original engineering contributions visible and cleanly layered.

## Decision

Build KoSpeaker as a **fork of woheller69/ttsEngine ("SherpaTTS", GPLv3)**, which runs neural voices on the **sherpa-onnx** (Apache-2.0, k2-fsa) runtime.

- Use **Piper** voices (VITS exported to ONNX) as the **default** engine: tiny (~30 MB), fast, and real-time on weak ARM — the right default for e-ink.
- Add **Kokoro-82M** (Apache-2.0, ~80 MB) as an **optional** high-quality mode for users who prefer richer prosody and can spare the extra compute (see [ROADMAP](../../ROADMAP.md)).
- Keep original KoSpeaker work (the reading pipeline, e-ink UI, KOReader onboarding) as clearly separable improvements layered on the fork.

## Alternatives considered

1. **Build fresh on sherpa-onnx.** Maximum control and a clean slate, but we would reimplement the Android system-TTS integration, the voice downloader, model management, and e-ink handling that SherpaTTS already provides and has proven on Boox. High effort, high risk, little differentiation.
2. **Fork NekoSpeak (MIT).** A permissive license would allow non-copyleft redistribution, but it lacks SherpaTTS's mature, e-ink-tested TTS-engine integration and built-in Piper downloader — the exact pieces most expensive to rebuild.
3. **Fork SherpaTTS (GPLv3) — chosen.** Provides proven Onyx Boox / e-ink support, a working system-TTS-engine registration, and a built-in Piper voice downloader out of the box. The cost is GPLv3 copyleft, which is acceptable for an open-source portfolio project. Lets us spend effort on the differentiating parts (reading pipeline, UX, onboarding) instead of plumbing.

## Consequences

- **GPLv3 lock-in.** Inheriting SherpaTTS's GPLv3 license means KoSpeaker and its derivatives must remain GPLv3. This is an accepted, deliberate trade-off; attribution to upstream and third parties is kept prominent and honest (see [NOTICE](../../NOTICE)).
- **The fork is the platform; the improvements are the showcase.** Because the base already handles engine registration and voice downloads, KoSpeaker's *original* value — prose normalization, sentence-chunked streaming, e-ink UI, and one-tap KOReader onboarding — sits as a distinct, reviewable layer. That layering is intentional: it is both good engineering and the portfolio's point of interest.
- **Piper default, Kokoro optional.** Defaulting to Piper guarantees a good experience on low-end e-ink hardware; making Kokoro-82M optional lets capable devices trade compute for prosody without penalizing the baseline. The engine choice is a user setting, not a hard-coded assumption.
- **Dependency on sherpa-onnx.** Neural inference is delegated to sherpa-onnx, so runtime performance and supported model formats track that project. This keeps the app small and avoids maintaining native inference code ourselves.
