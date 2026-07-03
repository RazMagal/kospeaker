# Testing KoSpeaker on an Onyx Boox

This is the hands-on checklist for loading a build onto a Boox and verifying each
voice in KOReader. Everything below is offline once the voice models are installed.

Nothing in this repo has been heard on a real device yet — the code compiles, packages,
and passes unit tests, but **audio quality (especially phonikud) needs on-device tuning**.
Use the "What to report back" notes to capture anything that sounds wrong.

## 1. Get the APK

Either:

- **From CI (no toolchain needed):** open the repo's
  [Actions](https://github.com/RazMagal/kospeaker/actions) → latest green run →
  **Artifacts → `debug-apk`** → download and unzip to get `app-debug.apk`.
- **Build locally:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  (needs JDK 17 + Android SDK).

The debug APK is ~177 MB (it bundles the eSpeak data and the sherpa + onnxruntime
native libraries for both ARM ABIs).

## 2. Sideload onto the Boox

- Transfer the APK to the device (USB/cloud) and tap it, allowing "install from unknown
  sources", **or** via ADB: `adb install -r app-debug.apk`.
- Open **KoSpeaker** once so it registers as a system TTS engine.

## 3. Make KoSpeaker the default voice

Android **Settings → System → Languages & input → Text-to-speech output** →
**Preferred engine → KoSpeaker**. Set speech **rate** and **pitch** to taste.
(KoSpeaker's own "Set up with KOReader" screen has a shortcut button for this.)

## 4. Install voices (per engine)

Open KoSpeaker → **Manage Languages**:

| Engine | How to install | Expectation |
|--------|----------------|-------------|
| **Piper** (default) | Pick a language; it downloads a Piper voice from Hugging Face | Fast, natural, e-ink friendly |
| **Kokoro** (high quality) | Kokoro section → download | Richer prosody, heavier |
| **Hebrew — MMS** | Run `scripts/convert_mms_hebrew.py`, then "Install from SD Card" → type **MMS** (see [HEBREW.md](HEBREW.md)) | Intelligible but flat, 16 kHz |
| **Hebrew — phonikud** (premium) | ADB-push the 3 model files into the `phonikud` model dir, type **Phonikud** (see [HEBREW.md](HEBREW.md#premium-hebrew-phonikud)) | Best Hebrew — **this is the one to scrutinize** |

## 5. Read an EPUB in KOReader

1. **Keep `audiobook.koplugin`** — KoSpeaker is the voice it drives, not a replacement.
2. Open an EPUB → menu → **Read Aloud**.
3. e-ink tip: disable page-turn animation for smoother playback.

## 6. What to report back (so I can fix it)

For each engine, note:

- **Does it speak at all?** (silence usually = wrong model files / engine not default)
- **First-word latency** on your CPU (the pipeline chunks by sentence to keep this low).
- **Naturalness** — robotic? wrong stress? dropped words?
- **Hebrew specifically:**
  - Are numbers spoken (they should be spelled out, since MMS's vocab has no digits)?
  - **phonikud:** are vowels/niqqud plausible, or garbled? Wrong homographs? Does it crash?
    (phonikud runs two ONNX models via onnxruntime — if it fails, `adb logcat | grep -iE 'kospeaker|phonikud|onnx'` while triggering Read Aloud will show why.)
- **Stop button** — does playback stop promptly mid-sentence?

Grab logs with `adb logcat -d > kospeaker-log.txt` right after reproducing an issue and
share the relevant lines. That's enough for me to iterate on the specific engine.
