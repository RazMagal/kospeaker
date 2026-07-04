package com.k2fsa.sherpa.onnx.tts.engine
import android.content.Context
import android.content.SharedPreferences

class PreferenceHelper(context: Context) {

    private val PREFS_NAME = "com.k2fsa.sherpa.onnx.tts.engine"
    private val SPEED_KEY = "speed"
    private val SID_KEY = "speaker_id"
    private val INIT_KEY = "init_espeak"
    private val USE_SYSTEM_SPEED = "apply_system_speed"
    private val STRIP_SSML = "strip_ssml"
    private val PRELOAD_MODEL = "preload_model"
    private val CURRENT_LANGUAGE = "current_language"
    private val VOLUME = "volume"

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setInitFinished() {
        val editor = sharedPreferences.edit()
        editor.putBoolean(INIT_KEY, true)
        editor.apply()
    }

    fun isInitFinished(): Boolean {
        return sharedPreferences.getBoolean(INIT_KEY, false)
    }

    fun setCurrentLanguage(language: String){
        val editor = sharedPreferences.edit()
        editor.putString(CURRENT_LANGUAGE, language)
        editor.apply()
    }

    fun getCurrentLanguage(): String? {
        return sharedPreferences.getString(CURRENT_LANGUAGE, "")
    }

    // Which installed voice is active for a given language code. Several voices may
    // share one language (e.g. two English voices); this pins the one to load. Stored
    // as the voice's unique model folder. Null/absent -> caller falls back to the first.
    fun setActiveVoiceFolder(language: String, folder: String) {
        val editor = sharedPreferences.edit()
        editor.putString("active_voice_$language", folder)
        editor.apply()
    }

    fun getActiveVoiceFolder(language: String): String? {
        return sharedPreferences.getString("active_voice_$language", null)
    }

    fun clearActiveVoiceFolder(language: String) {
        val editor = sharedPreferences.edit()
        editor.remove("active_voice_$language")
        editor.apply()
    }

    fun setApplySystemSpeed(useSystem: Boolean){
        val editor = sharedPreferences.edit()
        editor.putBoolean(USE_SYSTEM_SPEED, useSystem)
        editor.apply()
    }

    fun applySystemSpeed(): Boolean {
        return sharedPreferences.getBoolean(USE_SYSTEM_SPEED, false)
    }

    fun setStripSSML(stripSSML: Boolean){
        val editor = sharedPreferences.edit()
        editor.putBoolean(STRIP_SSML, stripSSML)
        editor.apply()
    }

    fun getStripSSML(): Boolean {
        return sharedPreferences.getBoolean(STRIP_SSML, false)
    }

    fun setPreloadModel(preloadModel: Boolean){
        val editor = sharedPreferences.edit()
        editor.putBoolean(PRELOAD_MODEL, preloadModel)
        editor.apply()
    }

    fun getPreloadModel(): Boolean {
        return sharedPreferences.getBoolean(PRELOAD_MODEL, false)
    }

    fun getVolume(): Float{
        return sharedPreferences.getFloat(VOLUME,1.0f)
    }
}