# KOReader Read Aloud on Onyx Boox with KoSpeaker

This guide sets up **natural, offline Read Aloud of EPUBs** in KOReader on an Onyx Boox e-ink device, using KoSpeaker as the system TTS engine.

The idea: KOReader has no speech of its own. Its Read Aloud plugin calls Android's system TTS and uses whatever engine is set as the **default**. So we install KoSpeaker, make it the default engine, and point KOReader at it.

---

## Prerequisites

- An Onyx Boox (or other Android e-ink) device.
- KOReader installed (from the Boox app store, F-Droid, or the KOReader releases page).
- A way to sideload files (USB, or the device's file manager / a download).

## Step 1 — Build or sideload the KoSpeaker APK

Option A — build from source (see the main [README](../README.md#install--build)):

```bash
./gradlew assembleDebug
```

The APK lands under `app/build/outputs/apk/debug/`. Copy it to the device and open it to install (you may need to allow "install from unknown sources" for your file manager).

Option B — download the debug APK from the project's CI build artifacts and sideload it the same way.

Option C — install over USB with ADB (fastest, and how the project is tested on-device):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable **Developer options → USB debugging** on the Boox first and accept the authorization prompt. `adb install -r` also upgrades an existing install in place, preserving your downloaded voices.

## Step 2 — Add a voice

1. Open **KoSpeaker**.
2. Tap **＋ (Add)** to open **Manage Languages**.
3. Pick a **Piper English** voice and let the app download it. Prefer a *medium*-quality voice (e.g. `hfc_female`) for the best balance of naturalness and speed on Boox hardware — `low` voices are faster but flatter, and `high` voices are the most detailed but the slowest to synthesize on e-ink CPUs.

Downloading is the **only** time KoSpeaker needs the network. After it finishes, everything works offline.

### Multiple voices & switching

KoSpeaker supports **several voices per language**, and you choose which one is active:

- Every voice you download (or sideload) gets its own slot; the newest one becomes active automatically.
- On the main screen, the **Language** dropdown picks the language and the **Voice** dropdown picks which installed voice speaks it.
- The **🗑 (Delete)** button removes just the voice currently shown in the **Voice** dropdown — not the whole language.

To sideload a voice's `model.onnx` + `tokens.txt` over USB, or for Hebrew, see **[HEBREW.md](HEBREW.md)** and **[TESTING_ON_BOOX.md](TESTING_ON_BOOX.md)**.

## Step 3 — Make KoSpeaker the default system TTS

1. Open Android **Settings**.
2. Go to **System → Languages & input → Text-to-speech output**.
   - On some Boox firmware this is under **Settings → Languages & input → Text-to-speech**, or reachable by searching Settings for "text-to-speech".
3. Set **Preferred engine** to **KoSpeaker**.
4. Adjust **Speech rate** and **Pitch** to taste. A slightly slower rate is often more comfortable for long reading sessions.
5. Use **Listen to an example** to confirm you hear the natural neural voice.

## Step 4 — Install the KOReader Read Aloud plugin

KOReader's Read Aloud lives in a plugin, `audiobook.koplugin`.

1. Download **[stradichenko/audiobook.koplugin](https://github.com/stradichenko/audiobook.koplugin)**.
2. Copy the `audiobook.koplugin` folder into KOReader's plugins directory:
   `koreader/plugins/audiobook.koplugin`
3. Fully close and reopen KOReader so it loads the plugin.

## Step 5 — Read an EPUB aloud

1. Open an EPUB in KOReader.
2. Open the top menu → find **Read Aloud** (under the tools/plugins menu).
3. Start playback. KOReader will speak through the system default TTS — which is now KoSpeaker — so you hear the neural offline voice.

## E-ink tip

Turn **off page-turn animations** in KOReader (and in the Boox system reader if it interferes). Animations cause extra e-ink refreshes and can make automatic page turns during Read Aloud look smeary and feel sluggish. Static, full-refresh page turns pair best with continuous audio.

---

## Troubleshooting

**KoSpeaker doesn't appear in the TTS engine list.**
Confirm the app installed and opened at least once. Some Boox firmware caches the engine list — reboot the device and check the TTS output settings again.

**The list shows KoSpeaker but there's no voice / it says data is missing.**
You haven't downloaded a voice yet, or the download didn't finish. Reopen KoSpeaker → **Manage Languages** and (re)download a Piper English voice. Make sure a language is actually selected as current.

**Read Aloud is missing from the KOReader menu.**
The plugin isn't loaded. Verify the folder is at `koreader/plugins/audiobook.koplugin` (the `.koplugin` folder itself must be directly inside `plugins/`), then fully restart KOReader.

**Read Aloud speaks with the old robotic voice.**
KOReader uses the *default* system engine. Recheck **Settings → Text-to-speech output** and ensure the preferred engine is **KoSpeaker**, not the stock Boox/eSpeak engine. Some devices have a separate "use default engine" toggle inside the reader — make sure it isn't pinned to another engine.

**Speech is choppy or starts slowly.**
Try a lower speech rate, and prefer a Piper voice over the heavier optional models on low-end hardware. The reading pipeline streams sentence by sentence, so the first sentence should begin quickly; long stalls usually mean the wrong (heavier) voice is selected.

**No sound at all.**
Check device volume and that no other audio app holds focus. Test with **Listen to an example** in the TTS settings first — if that is silent, the problem is the engine/voice, not KOReader.
