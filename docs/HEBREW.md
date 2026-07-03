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

## Coming later: premium Hebrew via phonikud

A markedly more natural route is planned: an on-device niqqud diacritizer
(`phonikud-onnx`) adds vowel points, converts to IPA, and drives a **22 kHz
Roboshaul VITS** voice. That removes most homograph errors and raises audio
quality well above MMS. Tracked in [`ROADMAP.md`](../ROADMAP.md).
