# Hebrew (offline) in KoSpeaker

KoSpeaker can read Hebrew EPUBs aloud fully offline using Meta's **MMS** voice
[`facebook/mms-tts-heb`](https://huggingface.co/facebook/mms-tts-heb). MMS is a
self-contained VITS model: it tokenises **raw Hebrew characters**, so it needs
no espeak phonemizer, no niqqud (vowel points), and no lexicon.

There is **no pre-built sherpa-onnx download** for Hebrew, so you convert the
model once on a computer, then sideload the two output files onto the device.

---

## 1. Convert the model (once, on a PC)

Requirements (Python 3.8-3.10, CPU is fine):

```bash
pip install "torch==1.13.1" onnx scipy Cython "numpy<2" requests
# git must also be on PATH
```

Run the converter (see [`scripts/convert_mms_hebrew.py`](../scripts/convert_mms_hebrew.py)):

```bash
python3 scripts/convert_mms_hebrew.py --out ./mms-heb
```

It downloads the Hebrew weights, clones the upstream VITS package, builds its
`monotonic_align` extension, and exports:

```
mms-heb/model.onnx     (~145 MB)
mms-heb/tokens.txt     (Hebrew character -> id table)
```

This follows the official sherpa-onnx MMS procedure:
<https://k2-fsa.github.io/sherpa/onnx/tts/mms.html>

---

## 2. Copy the files to the device

Copy `model.onnx` and `tokens.txt` anywhere the device file picker can reach
them (e.g. `Downloads/mms-heb/`) via USB, SD card, or cloud sync.

---

## 3. Install in KoSpeaker

1. Open **KoSpeaker -> Manage Languages -> Install from SD**.
2. Fill in the dialog:
   - **Language code**: `heb` (the 3-letter ISO 639-2/3 code)
   - **Model name**: e.g. `Hebrew (MMS)`
   - **Select model.onnx file** -> pick your `model.onnx`
   - **Select tokens.txt file** -> pick your `tokens.txt`
   - **Model type**: choose **MMS (facebook/mms-tts-\*)**  ← important
3. Tap **OK**.

> **Why the "MMS" model type matters.** It is stored as `vits-mms`, which tells
> the engine to load the model with an **empty dataDir and empty lexicon** so
> sherpa-onnx uses the **character frontend**. If you leave it on the default
> **Piper** type, the engine points dataDir at `espeak-ng-data`, sherpa selects
> the espeak frontend, and the Hebrew output turns to garbage.

---

## 4. Set KoSpeaker as the default Android TTS

Android **Settings -> System -> Languages & input -> Text-to-speech output** ->
set **Preferred engine** to **KoSpeaker**. (On Onyx Boox the path may be under
Settings -> Language, but the "Text-to-speech" screen is the same.)

---

## 5. Read a Hebrew EPUB in KOReader

Open a Hebrew EPUB, then **KOReader menu -> Speaker icon (Read Aloud) -> Start**.
KOReader sends the text to the Android TTS engine (KoSpeaker), which speaks it
with the MMS Hebrew voice. See [`KOREADER_SETUP.md`](KOREADER_SETUP.md) for the
Read Aloud setup details.

---

## Quality expectations

- **Sample rate 16 kHz.** Speech is **intelligible but flat** — usable for
  reading, but noticeably less natural than 22 kHz voices.
- **Unvocalized text -> homograph errors.** Hebrew is normally written without
  niqqud, so words that share spelling but differ in vowels/stress are sometimes
  mispronounced. MMS guesses from context and gets some wrong. This is a model
  limitation, not a bug.

## License

The MMS voices are **CC-BY-NC 4.0** (non-commercial). Using the converted model
for your own offline reading is fine; **do not redistribute it commercially**.

## Premium Hebrew (phonikud)

A markedly more natural, **22 kHz** route than MMS. Instead of feeding raw
characters to one model, KoSpeaker runs a two-stage on-device pipeline via
**onnxruntime-android**:

```
Hebrew text
  -> phonikud.onnx      (diacritizer / nakdan: adds niqqud + stress + vocal-shva)
  -> HebrewPhonemizer   (pure-Kotlin rule FST -> IPA phonemes)
  -> PiperTokenizer     (phoneme -> id sequence)
  -> shaul.onnx         (Roboshaul Piper VITS, single speaker)
  -> PCM float @ 22050 Hz
```

Adding niqqud first removes most of the homograph errors that plague MMS on
unvocalized text.

> **Status: on-device UNVERIFIED.** The pure-Kotlin core is unit-tested, but the
> ONNX tensor shapes, output head names and audio quality still need tuning on a
> real device. Treat this as experimental.

### Models to download

| File | Source | Size |
|------|--------|------|
| `phonikud.onnx` | <https://huggingface.co/thewh1teagle/phonikud-onnx/resolve/main/phonikud-1.0.int8.onnx> (rename to `phonikud.onnx`) | ~308 MB |
| `tokenizer.json` | from <https://huggingface.co/dicta-il/dictabert-large-char-menaked> (its `tokenizer.json`) | small |
| `shaul.onnx` | <https://huggingface.co/thewh1teagle/phonikud-tts-checkpoints/resolve/main/shaul.onnx> | ~64 MB |

(`model.config.json` is **not** required — the Piper phoneme→id map and inference
scales are embedded in `PiperTokenizer`.)

### Install

Because the models total ~372 MB, they are **not** copied through the SAF file
pickers. Instead you register the entry in the app and push the files manually:

1. Open **KoSpeaker -> Manage Languages -> Install from SD**.
2. Fill in:
   - **Language code**: `heb`
   - **Model name**: e.g. `Hebrew (phonikud)`
   - **Model type**: choose **Phonikud (Hebrew, premium)**
   - (the model/tokens file pickers are ignored for this type)
3. Tap **OK**. A toast shows the target folder, e.g.
   `/sdcard/Android/data/org.woheller69.ttsengine/files/heb`.
4. Push the three files (exact names) into that folder:

```bash
adb push phonikud.onnx    /sdcard/Android/data/org.woheller69.ttsengine/files/heb/phonikud.onnx
adb push tokenizer.json   /sdcard/Android/data/org.woheller69.ttsengine/files/heb/tokenizer.json
adb push shaul.onnx       /sdcard/Android/data/org.woheller69.ttsengine/files/heb/shaul.onnx
```

The engine loads them lazily on the first synthesis request for that language.

### Quality expectations

- **Sample rate 22 kHz** — clearer and more natural than the 16 kHz MMS voice.
- **On-device tuning required.** Audio quality, phoneme coverage and the ONNX
  I/O wiring are unverified on hardware; expect to iterate on shapes/scales
  before it sounds right.

### License

Both models are **CC-BY-NC** (non-commercial). Using them for your own offline
reading is fine; **do not redistribute them commercially**.
