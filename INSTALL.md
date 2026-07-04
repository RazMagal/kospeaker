# Installing KoSpeaker

KoSpeaker is an Android **system text-to-speech engine** that gives KOReader
natural, **offline** Read Aloud of EPUBs on Onyx Boox (and other Android e-ink)
devices. The flow is: install the app, make it the default TTS engine, and point
KOReader at it.

> This is the short version. For the full walkthrough — including e-ink tips and
> a troubleshooting section — see **[docs/KOREADER_SETUP.md](docs/KOREADER_SETUP.md)**.

## 1. Get the APK

- **Build it:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  (requires **JDK 17** and the **Android SDK**; `compileSdk 35`, `minSdk 29`).
- **Or** download the debug APK from the latest CI run's build artifacts.

## 2. Install it on the device

- **Over USB with ADB** (fastest): `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  (enable **Developer options → USB debugging** and accept the prompt; `-r`
  upgrades in place and keeps your voices).
- **Or** copy the APK to the device and open it (allow "install from unknown
  sources" for your file manager).

## 3. Add a voice

Open **KoSpeaker → ＋ Add → Manage Languages** and download a **Piper English**
voice — prefer a *medium* one for the best quality/speed balance on e-ink. This
is the only step that needs the network; everything else is offline.

KoSpeaker supports **multiple voices per language**: the **Language** dropdown
picks the language and the **Voice** dropdown picks which installed voice speaks
it. Hebrew and other sideloaded voices: **[docs/HEBREW.md](docs/HEBREW.md)**.

## 4. Make KoSpeaker the default TTS

Android **Settings → System → Languages & input → Text-to-speech output** → set
**KoSpeaker** as the preferred engine. The in-app **Set up with KOReader** screen
can open this for you and confirm KoSpeaker is the default.

## 5. Wire up KOReader

Install **[audiobook.koplugin](https://github.com/stradichenko/audiobook.koplugin)**
into `koreader/plugins/` (so the folder sits at `koreader/plugins/audiobook.koplugin`),
fully restart KOReader, open an EPUB, and start **Read Aloud**. KOReader speaks
through the system default engine — now KoSpeaker — so you hear the neural
offline voice.

---

Build details and project layout: **[README.md](README.md#install--build)**.
On-device / ADB testing notes: **[docs/TESTING_ON_BOOX.md](docs/TESTING_ON_BOOX.md)**.
