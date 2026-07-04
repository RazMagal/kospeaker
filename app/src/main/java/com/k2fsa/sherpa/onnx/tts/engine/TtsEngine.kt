package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.k2fsa.sherpa.onnx.tts.engine.phonikud.PhonikudEngine
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object TtsEngine {
    private val ttsCache = mutableMapOf<String, OfflineTts>()
    var tts: OfflineTts? = null

    // Premium offline Hebrew (phonikud). Non-null only while a "phonikud"-type model is loaded;
    // in that mode [tts] is null and TtsService routes synthesis through this engine instead.
    // ON-DEVICE UNVERIFIED (ONNX/audio path); see the phonikud subpackage.
    var phonikud: PhonikudEngine? = null

    // https://en.wikipedia.org/wiki/ISO_639-3
    var lang: String? = ""
    var country: String? = ""

    // Model sub-directory of the voice currently loaded. Unique per voice, so it is the
    // real cache key (several voices can share [lang]). Set by loadLanguageSettings().
    var folder: String = ""

    var volume: MutableState<Float> = mutableFloatStateOf(1.0F)
    var speed: MutableState<Float> = mutableFloatStateOf(1.0F)
    var speakerId: MutableState<Int> = mutableIntStateOf(0)

    private var modelName: String = "model.onnx"
    private var acousticModelName: String? = null // for matcha tts
    private var vocoder: String? = null // for matcha tts
    private var voices: String? = null // for kokoro
    private var ruleFsts: String? = null
    private var ruleFars: String? = null
    private var lexicon: String? = null
    private var dataDir: String = "espeak-ng-data"
    private var dictDir: String? = null

    // Architecture of the currently selected model (from LangDB COLUMN_TYPE).
    // Defaults to VITS/Piper when null/blank for backward compatibility with
    // existing installs and Migrate.java (which store "vits-piper").
    private var modelType: String? = null

    @JvmStatic
    fun getAvailableLanguages(context: Context): ArrayList<String> {
        val langCodes = java.util.ArrayList<String>()
        val db = LangDB.getInstance(context)
        val allLanguages = db.allInstalledLanguages
        for (language in allLanguages) {
            // Several voices can now share a language code; expose each code once so the
            // CHECK_TTS_DATA / available-languages list has no duplicates.
            if (!langCodes.contains(language.lang)) langCodes.add(language.lang)
        }
        return langCodes
    }

    fun stripSsmlTags(text: String): String {
        return Jsoup.parse(text).text().trim()
    }

    // The installed voice that should be active for [language]: the one the user pinned
    // via the active-voice preference, else the first row for that language. Returns null
    // only when no voice is installed for the language.
    private fun activeVoiceRow(context: Context, language: String): Language? {
        val rows = LangDB.getInstance(context).allInstalledLanguages.filter { it.lang == language }
        if (rows.isEmpty()) return null
        val active = PreferenceHelper(context).getActiveVoiceFolder(language)
        return rows.firstOrNull { it.folder == active } ?: rows.first()
    }

    @JvmStatic
    fun createTts(context: Context, language: String) {
        val row = activeVoiceRow(context, language)
        if (row == null) {
            Log.e(TAG, "createTts: no installed voice for language $language")
            return
        }
        val targetFolder = row.folder
        // Both engines are keyed by [folder], not [language], so switching to another
        // voice of the SAME language still reloads. A phonikud engine keeps [tts] null,
        // so guard it explicitly to avoid reloading the large ONNX models every request.
        if (phonikud != null && folder == targetFolder) {
            Log.i(TAG, "Phonikud already loaded: $targetFolder")
            return
        }
        if (tts != null && folder == targetFolder) {
            Log.i(TAG, "Already loaded: $targetFolder")
            return
        }
        if (ttsCache.containsKey(targetFolder)) {
            Log.i(TAG, "From TTS cache: $targetFolder")
            clearPhonikud() // switched away from a phonikud model to a cached sherpa one
            tts = ttsCache[targetFolder]
            loadLanguageSettings(context, language)
        } else {
            initTts(context, language)
        }
    }

    private fun clearPhonikud() {
        phonikud?.close()
        phonikud = null
    }

    // Fully release whatever voice is currently loaded and forget which one it was.
    // Needed when a voice is deleted: a phonikud engine lives in [phonikud] (not in
    // [ttsCache], and [tts] is null in phonikud mode), so evicting the cache alone would
    // leak the native engine and leave [folder] stale. Safe to call for sherpa voices too.
    fun clearLoaded() {
        clearPhonikud()
        tts = null
        folder = ""
    }

    private fun loadLanguageSettings(context: Context, language: String) {
        val currentLanguage = activeVoiceRow(context, language) ?: return
        this.lang = language
        this.country = currentLanguage.country
        this.folder = currentLanguage.folder
        this.speed.value = currentLanguage.speed
        this.speakerId.value = currentLanguage.sid
        this.volume.value = currentLanguage.volume
        this.modelType = currentLanguage.type
        val pref = PreferenceHelper(context)
        pref.setCurrentLanguage(language)
        // Pin the resolved voice so the choice is stable across restarts even if it
        // was chosen only by the "first row" fallback above.
        pref.setActiveVoiceFolder(language, currentLanguage.folder)
    }

    fun removeVoiceFromCache(folder: String) {
        ttsCache.remove(folder)
        Log.i(TAG, "Removed TTS cache for: $folder")
        Log.i(TAG, "TTS cache size:"+ ttsCache.size)
    }

    private fun initTts(context: Context, lang: String) {
        Log.i(TAG, "Add to TTS cache: " + lang)

        loadLanguageSettings(context, lang)

        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath

        // [folder] was resolved by loadLanguageSettings() above; it is unique per voice.
        val modelDir = "$externalFilesDir/$folder"

        // Premium offline Hebrew (phonikud): run two onnxruntime-android models instead of the
        // sherpa OfflineTts. Model dir must hold phonikud.onnx, tokenizer.json and shaul.onnx.
        // ON-DEVICE UNVERIFIED. On failure we leave both engines null so TtsService errors
        // gracefully rather than crashing.
        if (modelType?.startsWith("phonikud") == true) {
            clearPhonikud()
            tts = null
            phonikud = try {
                PhonikudEngine.create(modelDir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load phonikud engine from $modelDir", e)
                null
            }
            Log.i(TAG, "Phonikud engine loaded=${phonikud != null} for $lang")
            return
        }
        // Any non-phonikud model: make sure a previously loaded phonikud engine is released.
        clearPhonikud()

        // Reset the per-architecture frontend fields on every load so that
        // switching languages never leaks paths from a previously loaded model.
        //
        // MMS (facebook/mms-tts-*) is a self-contained VITS that ships its own
        // character tokens: no espeak, no lexicon, no dataDir. sherpa-onnx picks
        // the character frontend ONLY when BOTH dataDir and lexicon are empty; if
        // espeak-ng-data is passed as dataDir it selects the espeak frontend and
        // MMS produces garbage. So force dataDir/lexicon/voices empty for MMS and
        // keep the bundled espeak-ng-data for Piper/Coqui/Kokoro.
        val isMms = modelType?.startsWith("vits-mms") == true
        val isKokoro = modelType?.startsWith("kokoro") == true
        dataDir = if (isMms) "" else "espeak-ng-data"
        lexicon = if (isMms) "" else lexicon
        voices = if (isKokoro) "voices.bin" else ""

        var newDataDir = ""
        if (dataDir.isNotEmpty()) {
            newDataDir = copyDataDir(context, dataDir)
        }

        if (dictDir != null) {
            val newDir = copyDataDir(context, dictDir!!)
            dictDir = "$newDir/$dictDir"
            ruleFsts = "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst"
        }

        // getOfflineTtsConfig() (sherpa-onnx v1.13.0) auto-populates the Kokoro
        // sub-config when `voices` is non-empty, otherwise it builds the
        // VITS/Piper sub-config (used by Piper, Coqui and MMS). The frontend
        // (espeak vs character) is then selected from dataDir/lexicon, which were
        // set per-architecture above.
        val config = getOfflineTtsConfig(
            modelDir = modelDir!!,
            modelName = modelName ?: "",
            acousticModelName = acousticModelName ?: "",
            vocoder = vocoder ?: "",
            voices = voices ?: "",
            lexicon = lexicon ?: "",
            dataDir = newDataDir ?: "",
            dictDir = dictDir ?: "",
            ruleFsts = ruleFsts ?: "",
            ruleFars = ruleFars ?: ""
        )

        val configDebugOff = config.copy(  // create a new instance with debug switched off
            model = config.model.copy(debug = false)
        )

        tts = OfflineTts(assetManager = null, config = configDebugOff)
        ttsCache[folder] = tts!!
        Log.i(TAG, "TTS cache size:"+ ttsCache.size)
    }

    private fun copyDataDir(context: Context, dataDir: String): String {
        Log.i(TAG, "data dir is $dataDir")
        if (!PreferenceHelper(context).isInitFinished()){  //only copy at first startup
            copyAssets(context, dataDir)
            PreferenceHelper(context).setInitFinished()
        }
        val newDataDir = context.getExternalFilesDir(null)!!.absolutePath + "/" + dataDir
        Log.i(TAG, "newDataDir: $newDataDir")
        return newDataDir
    }

    private fun copyAssets(context: Context, path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets!!.isEmpty()) {
                copyFile(context, path)
            } else {
                val fullPath = "${context.getExternalFilesDir(null)}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets.iterator()) {
                    val p: String = if (path == "") "" else "$path/"
                    copyAssets(context, p + asset)
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
        }
    }

    private fun copyFile(context: Context, filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = context.getExternalFilesDir(null)!!.absolutePath + "/" + filename
            val file = File(newFilename)
            if (!file.exists()) {
                val ostream = FileOutputStream(newFilename)
                val buffer = ByteArray(1024)
                var read = 0
                while (read != -1) {
                    ostream.write(buffer, 0, read)
                    read = istream.read(buffer)
                }
                istream.close()
                ostream.flush()
                ostream.close()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename, $ex")
        }
    }
}
