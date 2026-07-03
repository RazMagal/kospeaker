# ADR 0002: Premium Hebrew TTS via phonikud

- **Status:** Proposed
- **Date:** 2026-07

## Context

KoSpeaker already reads Hebrew offline with Meta's `facebook/mms-tts-heb` (see
[`HEBREW.md`](../HEBREW.md)), but the quality ceiling is low:

- **16 kHz, flat prosody.** MMS synthesises at 16 kHz; the voice is intelligible
  but noticeably less natural than a 22 kHz voice.
- **No niqqud → homograph errors.** Hebrew is normally written unvocalised. MMS
  tokenises raw characters and guesses vowels/stress from context, so
  spelling-identical words (e.g. סֵפֶר "book" vs סַפָּר "barber") are often
  mispronounced. This is a model limitation, not a bug.

The goal: a markedly more natural, still fully offline Hebrew voice for
long-form Read Aloud on weak e-ink ARM CPUs. The **phonikud** stack by
`thewh1teagle` ([paper, arXiv:2506.12311](https://arxiv.org/abs/2506.12311))
targets exactly this. Its thesis: augment a Hebrew diacritizer to emit
fully-specified IPA (including stress and vocal-schwa, which written Hebrew
omits), and small local VITS voices fed that IPA "approach large proprietary
systems."

## Decision

Adopt phonikud as an **optional premium Hebrew engine**: an on-device niqqud
diacritizer (`phonikud-1.0.int8.onnx`) → IPA phonemiser → a 22.05 kHz
Roboshaul/SASPEECH VITS voice (`shaul.onnx`). MMS stays as the lighter default.

## Architecture / data flow

```
Hebrew text (no niqqud)
      │  phonikud-onnx diacritizer  (onnxruntime)
      ▼
Vocalised Hebrew + phonikud marks  (niqqud + stress ˈ + vocal-schwa)
      │  phonikud phonemiser        (deterministic, rule-based)
      ▼
IPA phoneme string  (e.g. "ˈsefeʁ")
      │  Piper-style tokeniser: IPA→ids via phoneme_id_map,
      │  with BOS/EOS + interleaved pad(0) blanks
      ▼
shaul.onnx  (VITS, phoneme_type=raw, sample_rate=22050)
      ▼
float PCM → 16-bit PCM → AudioTrack
```

The voice's `model.config.json` is **Piper format**: `sample_rate` 22050,
`phoneme_type` "raw", 256 declared symbols (~157 populated in
`phoneme_id_map`, IPA consonants/vowels plus stress `ˈ ˌ` and length `ː`),
`num_speakers` 1, `noise_scale` 0.667, `length_scale` 1.0, `noise_w` 0.8.

**Integration challenge.** phonikud produces *raw IPA*. sherpa-onnx's built-in
frontends do not match it: the **espeak** frontend (Piper default) would
re-phonemise already-IPA text into garbage; the **character** frontend maps
Unicode scalars 1:1 but will **not** reproduce Piper's required
BOS/EOS framing or the pad(0) blank interleaved between every phoneme, and does
not honour the voice's `phoneme_id_map`. Two options:

| Option | Mechanism | Pros | Cons |
|---|---|---|---|
| **(a) sherpa + custom tokens.txt** | Treat IPA as "text", character frontend, tokens.txt built from `phoneme_id_map` | Reuses sherpa streaming/callback path; one runtime | No pad/BOS interleaving; multi-codepoint symbols and blanks mismatch → wrong ids/prosody; fragile |
| **(b) direct onnxruntime-android** | Run `shaul.onnx` via `OrtSession`; hand-written Piper tokeniser (id map + BOS/EOS + pad blanks); float→PCM ourselves | Faithful, exact control; diacritizer + voice share one runtime | Reimplements VITS input prep + audio; bypasses sherpa's `generateWithConfigAndCallback` |

**Recommendation: option (b).** Correctness hinges on reproducing Piper's exact
input format, which sherpa's generic frontends cannot. Direct onnxruntime keeps
the whole Hebrew path (diacritizer + voice) on one dependency and one code path
we fully control; the cost is a small, contained tokeniser + PCM shim.

## KoSpeaker integration points

- New `modelType` value `phonikud` alongside `vits-piper`/`vits-mms`/`kokoro`
  in [`TtsEngine.initTts`](../../app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsEngine.kt).
  It short-circuits the sherpa `OfflineTts` build and instantiates a dedicated
  `PhonikudTts` (two `OrtSession`s: diacritizer + voice).
- **Model acquisition:** sideload/download (never bundled). Diacritizer
  `phonikud-1.0.int8.onnx` (~308 MB) + `shaul.onnx` (~64 MB) + a small
  `tokens.json` derived from `model.config.json`. Extend Manage Languages
  "Install from SD".
- **Phoneme mapping** lives in app assets (generated once from
  `model.config.json`), not downloaded, so it stays version-locked to the voice.
- **Streaming:** [`TtsService`](../../app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsService.kt)'s
  `normalize → SentenceChunker.chunk → stream` loop is unchanged; diacritise
  **per chunk** (not whole utterance) so first-audio latency stays low and
  `onStop()` can still interrupt between chunks.

## Dependencies & footprint

- Add `com.microsoft.onnxruntime:onnxruntime-android` (the Java `OrtSession`
  API). sherpa-onnx v1.13.0 bundles onnxruntime **native** `.so` but not the
  Java API; expect a duplicate `libonnxruntime.so` → resolve with
  `packaging { jniLibs.pickFirsts }` (verify at build time).
- Footprint: diacritizer int8 (~308 MB, listed size — dominant cost; verify
  actual on-device) + voice (~64 MB). RAM for two INT8/FP32 sessions is the main
  concern on Boox; diacritizer latency on weak ARM is the likely bottleneck.

## Licensing

phonikud diacritizer is **MIT**; the Roboshaul/SASPEECH voices are
**CC-BY-NC 4.0** (non-commercial). Fine for personal/open KoSpeaker. Models are
downloaded/sideloaded, **not bundled**, so the GPLv3 app code is unaffected.
Flag CC-BY-NC before any commercial distribution.

## Risks / why deferred

- Correctness of the IPA→id tokeniser (pad/BOS/EOS, multi-codepoint symbols) and
  audio scaling can only be validated on a **real Android build with on-device
  listening** — impossible in the current no-SDK CI environment.
- High effort; large diacritizer download; latency unproven on e-ink ARM.

**Acceptance criteria:** (1) builds with both onnxruntime layers, no `.so`
clash; (2) a known homograph pair is disambiguated correctly; (3) audio is clean
22 kHz with no clipping/noise; (4) per-chunk diacritise+synthesise is
near-real-time on a target Boox; (5) Stop interrupts within one chunk.

## Alternatives considered

1. **MMS-heb (current default).** Self-contained, tiny frontend, no niqqud —
   16 kHz and homograph errors. Kept as the light default.
2. **HebTTS (VALL-E-style).** Higher quality but LM-scale and far too heavy for
   real-time e-ink synthesis.
3. **Online diacritizers (Dicta API).** Accurate niqqud but network-dependent —
   violates the offline-first constraint.
