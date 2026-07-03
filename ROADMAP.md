# KoSpeaker Roadmap

Milestones toward a polished, offline, neural TTS engine for KOReader Read Aloud on e-ink devices. Each item is delivered as one focused change set through the [development loop](LOOP.md), kept green in CI, and verified on-device where relevant.

## Milestones

- [x] **Fork base — SherpaTTS v3.3.** Start from a mature, GPLv3 Android TTS engine that already registers as a system engine, ships a built-in Piper voice downloader, and is known to run on Onyx Boox. This gives KoSpeaker a working, e-ink-proven foundation instead of a blank slate.
- [x] **Reading pipeline — normalization + sentence chunking + streaming.** Add a prose-oriented text pipeline: normalize EPUB text (whitespace, punctuation, abbreviations, stray markup) and split it into sentence-sized chunks that stream to audio. Lowers time-to-first-word and keeps playback responsive despite slow e-ink refresh.
- [x] **Kokoro-82M optional high-quality engine.** Offer Kokoro-82M (~80 MB, Apache-2.0) as a selectable high-quality mode alongside the default Piper voices. Richer prosody for users who prefer quality over speed, without forcing the heavier model on low-end hardware. _Implemented (model-type branch in `TtsEngine`, Kokoro downloader + catalog entry `kokoro-en-v0_19`); pending on-device/CI build verification._
- [x] **Hebrew voice (offline) via MMS-heb (sideload + convert script) + Hebrew-aware reading pipeline.** Enable offline Hebrew with Meta's self-contained `facebook/mms-tts-heb` VITS (character frontend, no espeak/niqqud/lexicon). Ships a one-shot converter ([`scripts/convert_mms_hebrew.py`](scripts/convert_mms_hebrew.py)) producing `model.onnx` + `tokens.txt`, a `vits-mms` model-type branch in `TtsEngine` (empty dataDir/lexicon), an "MMS" choice in the Install-from-SD dialog, and an end-to-end guide ([`docs/HEBREW.md`](docs/HEBREW.md)). _Pending on-device/CI build verification._
- [ ] **Premium Hebrew via phonikud.** On-device niqqud diacritizer (`phonikud-onnx`) adds vowel points -> IPA -> a 22 kHz Roboshaul VITS voice, for markedly more natural Hebrew (removes most homograph errors and lifts audio quality well above 16 kHz MMS).
- [ ] **E-ink-friendly UI.** Rework the interface for e-ink: high-contrast (near-black-on-white), no animations, and large tap targets. Makes the app comfortable to operate on a slow, grayscale, touch-imprecise Boox screen.
- [x] **Guided "Set up with KOReader" helper.** A `SetupActivity` that opens the system TTS settings (so the user can pick KoSpeaker as default + tune speed/pitch), shows a live "is KoSpeaker the default engine?" status, and walks through the KOReader Read Aloud steps — including the note to KEEP `audiobook.koplugin` (KoSpeaker is the voice it drives, not a replacement) and an e-ink page-turn tip. Removes the most error-prone part of onboarding. _Builds locally; on-device UX pending._
- [ ] **Per-book voice/speed presets.** Remember voice, speech rate, and pitch per book (or per genre), so fiction and technical reading can each have their own comfortable settings without re-tuning every session.
- [ ] **CI release APK.** Automate signed release-APK builds in CI and publish them as downloadable artifacts/releases, so users can install stable versions without a local toolchain.

## Exit criteria

The core is considered "done" when the neural reading experience (Piper default + reading pipeline) and the KOReader onboarding path are complete, CI is green, and Read Aloud has been verified end-to-end on an Onyx Boox device. Remaining items (Kokoro, presets, release automation) are enhancements layered on top of that baseline.
