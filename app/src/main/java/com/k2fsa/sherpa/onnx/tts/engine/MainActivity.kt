@file:OptIn(ExperimentalMaterial3Api::class)

package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import com.k2fsa.sherpa.onnx.tts.engine.reading.SentenceChunker
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.woheller69.freeDroidWarn.FreeDroidWarn
import java.io.File

const val TAG = "sherpa-onnx-tts-engine"

class MainActivity : ComponentActivity() {

    private lateinit var track: AudioTrack

    private var stopped: Boolean = false

    private var samplesChannel = Channel<FloatArray>()
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var langDB: LangDB

    // True while the TTS engine (esp. the 308MB phonikud diacritizer) loads on a
    // background coroutine. Compose reads this to show a "Loading voice…" hint and
    // disable Play until the model is ready. Written on the main thread only.
    private val modelLoading = mutableStateOf(true)

    override fun onPause() {
        super.onPause()
        samplesChannel.close()
    }

    override fun onResume() {
        //Reset speed in case it has been changed by TtsService
        val db = LangDB.getInstance(this)
        val allLanguages = db.allInstalledLanguages
        // TtsEngine.folder may still be unset while the engine loads asynchronously
        // (see onCreate), so guard with firstOrNull to avoid crashing on first open.
        allLanguages.firstOrNull { it.folder == TtsEngine.folder }
            ?.let { TtsEngine.speed.value = it.speed }
        super.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceHelper = PreferenceHelper(this)
        langDB = LangDB.getInstance(this)
        Migrate.renameModelFolder(this)   //Rename model folder if "old" structure
        if (!preferenceHelper.getCurrentLanguage().equals("")) {
            // Show the UI immediately, then load the engine OFF the main thread. The
            // premium Hebrew (phonikud) diacritizer is ~308MB and would otherwise
            // freeze app open on the main thread. Play stays disabled (modelLoading)
            // until the load finishes; sherpa engines load fast but use the same path.
            modelLoading.value = true
            setupDisplay(langDB, preferenceHelper)
            ThemeUtil.setStatusBarAppearance(this)
            FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)
            if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(
                this,
                "https://github.com/woheller69/ttsengine"
            )
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        TtsEngine.createTts(this@MainActivity, preferenceHelper.getCurrentLanguage()!!)
                    }
                } catch (e: Exception) {
                    // Don't hang the UI on a failed load: log and fall through to
                    // clear the loading state below so Play re-enables.
                    Log.e(TAG, "Failed to load TTS engine", e)
                } finally {
                    // Back on the main thread (lifecycleScope defaults to Main). Init
                    // the AudioTrack now that the engine's sample rate is known, then
                    // clear the loading flag to enable Play and refresh speaker info.
                    initAudioTrack()
                    modelLoading.value = false
                }
            }
        } else {
            val intent = Intent(this, ManageLanguagesActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun restart() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    private fun setupDisplay(
        langDB: LangDB,
        preferenceHelper: PreferenceHelper
    ) {
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("SherpaTTS") },
                            actions = {
                                IconButton(
                                    onClick = {
                                        startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://github.com/woheller69/ttsengine")
                                            )
                                        )
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = colorResource(
                                            R.color.primaryDark
                                        )
                                    )
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = "Info")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                intent = Intent()
                                intent.setAction("com.android.settings.TTS_SETTINGS")
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                this.startActivity(intent)
                                finish()
                            }
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "TTS Settings")
                        }
                    }) {
                    Box(modifier = Modifier.padding(it)) {
                        // Seed the sample from the persisted language, not TtsEngine.lang,
                        // since the engine may still be loading asynchronously (see onCreate).
                        var sampleText by remember { mutableStateOf(getSampleText(preferenceHelper.getCurrentLanguage() ?: "")) }
                        val allLanguages = langDB.allInstalledLanguages
                        val currentLangCode = preferenceHelper.getCurrentLanguage() ?: ""
                        // Distinct language codes for the Language picker; the voices sharing
                        // the current language for the Voice picker.
                        val distinctLangs = allLanguages.map { it.lang }.distinct()
                        val voicesForCurrent = allLanguages.filter { it.lang == currentLangCode }
                        val activeFolder = preferenceHelper.getActiveVoiceFolder(currentLangCode)
                            ?: voicesForCurrent.firstOrNull()?.folder
                        // Reading modelLoading here subscribes this scope, so when the async
                        // load finishes the UI recomposes: numSpeakers is re-read and Play
                        // re-enables. True until the engine (esp. 308MB phonikud) is ready.
                        val loading = modelLoading.value
                        // Phonikud (premium Hebrew) keeps TtsEngine.tts null and is single-speaker.
                        val numSpeakers = TtsEngine.tts?.numSpeakers() ?: 1

                        LazyColumn( // ✅ LazyColumn replaces Column
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Entry point to the guided "Set up with KOReader" screen.
                            item {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorResource(R.color.primaryDark),
                                        contentColor = colorResource(R.color.white)
                                    ),
                                    onClick = {
                                        startActivity(
                                            Intent(applicationContext, SetupActivity::class.java)
                                        )
                                    }
                                ) {
                                    Text(getString(R.string.setup_title))
                                }
                            }
                            item {
                                Text(
                                    getString(R.string.speed) + " " + String.format(
                                        "%.1f",
                                        TtsEngine.speed.value
                                    )
                                )
                            }
                            item {
                                Slider(
                                    value = TtsEngine.speed.value,
                                    onValueChange = {
                                        TtsEngine.speed.value = it
                                    },
                                    onValueChangeFinished = {
                                        langDB.updateVoiceSettings(
                                            TtsEngine.folder,
                                            TtsEngine.speakerId.value,
                                            TtsEngine.speed.value,
                                            TtsEngine.volume.value
                                        )
                                    },
                                    valueRange = 0.2F..3.0F,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = colorResource(R.color.primaryDark),
                                        activeTrackColor = colorResource(R.color.primaryDark)
                                    )
                                )
                            }

                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    var applySystemSpeed by remember {
                                        mutableStateOf(
                                            preferenceHelper.applySystemSpeed()
                                        )
                                    }
                                    Checkbox(
                                        checked = applySystemSpeed,
                                        onCheckedChange = { isChecked ->
                                            preferenceHelper.setApplySystemSpeed(isChecked)
                                            applySystemSpeed = isChecked
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colorResource(R.color.primaryDark)
                                        )
                                    )
                                    Text(
                                        getString(R.string.apply_system_speed)
                                    )
                                }
                            }

                            item { Spacer(modifier = Modifier.height(10.dp)) }

                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    var stripSSML by remember {
                                        mutableStateOf(
                                            preferenceHelper.getStripSSML()
                                        )
                                    }
                                    Checkbox(
                                        checked = stripSSML,
                                        onCheckedChange = { isChecked ->
                                            preferenceHelper.setStripSSML(isChecked)
                                            stripSSML = isChecked
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colorResource(R.color.primaryDark)
                                        )
                                    )
                                    Text(
                                        getString(R.string.strip_ssml)
                                    )
                                }
                            }

                            item { Spacer(modifier = Modifier.height(10.dp)) }

                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    var preloadModel by remember {
                                        mutableStateOf(
                                            preferenceHelper.getPreloadModel()
                                        )
                                    }
                                    Checkbox(
                                        checked = preloadModel,
                                        onCheckedChange = { isChecked ->
                                            preferenceHelper.setPreloadModel(isChecked)
                                            preloadModel = isChecked
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colorResource(R.color.primaryDark)
                                        )
                                    )
                                    Text(
                                        getString(R.string.preload_model)
                                    )
                                }
                            }


                            item { Spacer(modifier = Modifier.height(10.dp)) }

                            // Language picker: one entry per distinct installed language.
                            item {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    var expanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = it }
                                    ) {
                                        val keyboardController =
                                            LocalSoftwareKeyboardController.current
                                        OutlinedTextField(
                                            value = currentLangCode,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(getString(R.string.language_id)) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        expanded = true
                                                        keyboardController?.hide()
                                                    }
                                                },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = "Dropdown"
                                                )
                                            }
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            distinctLangs.forEach { langCode ->
                                                DropdownMenuItem(
                                                    text = { Text(langCode) },
                                                    onClick = {
                                                        preferenceHelper.setCurrentLanguage(langCode)
                                                        expanded = false
                                                        restart()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Voice picker: which installed voice is active for the current
                            // language. Only shown when the language actually has voices.
                            if (voicesForCurrent.isNotEmpty()) {
                                item { Spacer(modifier = Modifier.height(10.dp)) }
                                item {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        var expanded by remember { mutableStateOf(false) }
                                        val activeVoice =
                                            voicesForCurrent.firstOrNull { it.folder == activeFolder }
                                                ?: voicesForCurrent.first()
                                        ExposedDropdownMenuBox(
                                            expanded = expanded,
                                            onExpandedChange = { expanded = it }
                                        ) {
                                            val keyboardController =
                                                LocalSoftwareKeyboardController.current
                                            OutlinedTextField(
                                                value = if (activeVoice.name.isNotEmpty()) activeVoice.name else activeVoice.folder,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text(getString(R.string.voice_id)) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor()
                                                    .onFocusChanged { focusState ->
                                                        if (focusState.isFocused) {
                                                            expanded = true
                                                            keyboardController?.hide()
                                                        }
                                                    },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        contentDescription = "Dropdown"
                                                    )
                                                }
                                            )
                                            ExposedDropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                voicesForCurrent.forEach { voice ->
                                                    val label = if (voice.name.isNotEmpty()) voice.name else voice.folder
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            preferenceHelper.setActiveVoiceFolder(
                                                                currentLangCode,
                                                                voice.folder
                                                            )
                                                            expanded = false
                                                            restart()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (numSpeakers > 1) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        var expanded by remember { mutableStateOf(false) }
                                        val speakerList = (0 until numSpeakers).toList()
                                        var selectedSpeaker by remember { mutableStateOf(TtsEngine.speakerId) }
                                        val keyboardController =
                                            LocalSoftwareKeyboardController.current

                                        ExposedDropdownMenuBox(
                                            expanded = expanded,
                                            onExpandedChange = { expanded = it }
                                        ) {
                                            OutlinedTextField(
                                                value = selectedSpeaker.toString(),
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text(getString(R.string.speaker_id) + " " + "(0-${numSpeakers - 1})") },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor()
                                                    .onFocusChanged { focusState ->
                                                        if (focusState.isFocused) {
                                                            expanded = true
                                                            keyboardController?.hide()
                                                        }
                                                    },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        contentDescription = "Dropdown"
                                                    )
                                                }
                                            )
                                            ExposedDropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                speakerList.forEach { speakerId ->
                                                    DropdownMenuItem(
                                                        text = { Text(speakerId.toString()) },
                                                        onClick = {
                                                            selectedSpeaker.value = speakerId
                                                            TtsEngine.speakerId.value = speakerId
                                                            langDB.updateVoiceSettings(
                                                                TtsEngine.folder,
                                                                TtsEngine.speakerId.value,
                                                                TtsEngine.speed.value,
                                                                TtsEngine.volume.value
                                                            )
                                                            expanded = false
                                                            stopped = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Row {
                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            val intent = Intent(
                                                applicationContext,
                                                ManageLanguagesActivity::class.java
                                            )
                                            startActivity(intent)
                                        }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_add_24dp),
                                            contentDescription = stringResource(id = R.string.add_language)
                                        )
                                    }

                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            deleteActiveVoice()
                                        }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_delete_24dp),
                                            contentDescription = stringResource(id = R.string.delete_language)
                                        )
                                    }
                                }
                            }

                            item {
                                OutlinedTextField(
                                    value = sampleText,
                                    onValueChange = { sampleText = it },
                                    label = { Text(getString(R.string.input)) },
                                    maxLines = 10,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .wrapContentHeight(),
                                    singleLine = false
                                )
                            }


                            item {
                                Text(
                                    getString(R.string.volume) + " " + String.format(
                                        "%.1f",
                                        TtsEngine.volume.value
                                    )
                                )
                            }

                            item {
                                Slider(
                                    value = TtsEngine.volume.value,
                                    onValueChange = {
                                        TtsEngine.volume.value = it
                                    },
                                    onValueChangeFinished = {
                                        langDB.updateVoiceSettings(
                                            TtsEngine.folder,
                                            TtsEngine.speakerId.value,
                                            TtsEngine.speed.value,
                                            TtsEngine.volume.value
                                        )
                                    },
                                    valueRange = 0.2F..5.0F,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = colorResource(R.color.primaryDark),
                                        activeTrackColor = colorResource(R.color.primaryDark)
                                    )
                                )
                            }

                            // Shown while the engine loads asynchronously (see onCreate);
                            // pairs with the disabled Play button below.
                            if (loading) {
                                item {
                                    Text(
                                        text = "Loading voice…",
                                        modifier = Modifier.padding(start = 5.dp, bottom = 4.dp)
                                    )
                                }
                            }

                            item {
                                Row {
                                    Button(
                                        // Disabled until the model finishes loading so a tap
                                        // before the engine is ready can't hit a null engine.
                                        enabled = !loading,
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            if (sampleText.isBlank() || sampleText.isEmpty()) {
                                                Toast.makeText(
                                                    applicationContext,
                                                    getString(R.string.input),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                stopped = false

                                                track.pause()
                                                track.flush()
                                                track.play()

                                                samplesChannel = Channel<FloatArray>()

                                                CoroutineScope(Dispatchers.IO).launch {
                                                    for (samples in samplesChannel) {
                                                        for (i in samples.indices) {
                                                            samples[i] *= TtsEngine.volume.value
                                                        }
                                                        track.write(
                                                            samples,
                                                            0,
                                                            samples.size,
                                                            AudioTrack.WRITE_BLOCKING
                                                        )
                                                    }
                                                }

                                                if (preferenceHelper.getStripSSML()) sampleText = TtsEngine.stripSsmlTags(sampleText)

                                                CoroutineScope(Dispatchers.Default).launch {
                                                    val phonikud = TtsEngine.phonikud
                                                    if (phonikud != null) {
                                                        // Premium Hebrew FIX: run the heavy 308MB
                                                        // diacritizer ONCE over the full text, THEN
                                                        // chunk the diacritized text and stream the
                                                        // fast VITS voice per sentence via the same
                                                        // ::callback -> AudioTrack path (avoids the
                                                        // per-chunk diacritizer stutter).
                                                        val diacritized = phonikud.diacritize(sampleText)
                                                        for (chunk in SentenceChunker.chunk(diacritized)) {
                                                            if (stopped) break
                                                            val samples = phonikud.synthesizeDiacritized(chunk)
                                                            if (samples.isNotEmpty()) callback(samples)
                                                        }
                                                    } else {
                                                        TtsEngine.tts!!.generateWithCallback(
                                                            text = sampleText,
                                                            sid = TtsEngine.speakerId.value,
                                                            speed = TtsEngine.speed.value,
                                                            callback = ::callback,
                                                        )
                                                    }
                                                }.start()
                                            }
                                        }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_play_24dp),
                                            contentDescription = stringResource(id = R.string.play)
                                        )
                                    }

                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            stopped = true
                                            // track is initialized only after the async load
                                            // (see onCreate); guard so a Stop tap during
                                            // loading can't hit the lateinit track.
                                            if (this@MainActivity::track.isInitialized) {
                                                track.pause()
                                                track.flush()
                                            }
                                        }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_stop_24dp),
                                            contentDescription = stringResource(id = R.string.stop)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete only the voice that is currently active for the current language (not every
    // voice sharing that language). Falls back to another voice of the same language, then
    // to any other installed language.
    private fun deleteActiveVoice() {
        val currentLang = preferenceHelper.getCurrentLanguage()
        if (currentLang.isNullOrEmpty()) { restart(); return }
        val rows = langDB.allInstalledLanguages.filter { it.lang == currentLang }
        if (rows.isEmpty()) { restart(); return }
        val activeFolder = preferenceHelper.getActiveVoiceFolder(currentLang)
        val victim = rows.firstOrNull { it.folder == activeFolder } ?: rows.first()

        // Release the engine + its cache entry before deleting the files. clearLoaded()
        // also releases a live phonikud engine and resets the stale folder, which a bare
        // cache eviction would miss.
        TtsEngine.removeVoiceFromCache(victim.folder)
        TtsEngine.clearLoaded()

        // Guard against a blank folder, which would resolve to the whole files dir.
        if (victim.folder.isNotBlank()) {
            val subdirectory = File(getExternalFilesDir(null), victim.folder)
            if (subdirectory.exists() && subdirectory.isDirectory) {
                subdirectory.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
                subdirectory.delete()
            }
        }
        langDB.removeByFolder(victim.folder)
        preferenceHelper.clearActiveVoiceFolder(currentLang)

        // Choose what to show next: another voice of this language, else another language.
        val remainingSameLang = langDB.allInstalledLanguages.filter { it.lang == currentLang }
        if (remainingSameLang.isNotEmpty()) {
            preferenceHelper.setActiveVoiceFolder(currentLang, remainingSameLang.first().folder)
        } else {
            val all = langDB.allInstalledLanguages
            if (all.isEmpty()) preferenceHelper.setCurrentLanguage("")
            else preferenceHelper.setCurrentLanguage(all.first().lang)
        }
        restart()
    }

    override fun onDestroy() {
        if (this::track.isInitialized) track.release()
        super.onDestroy()
    }

    // this function is called from C++
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            val samplesCopy = samples.copyOf()
            CoroutineScope(Dispatchers.IO).launch {
                if (!samplesChannel.isClosedForSend) samplesChannel.send(samplesCopy)
            }
            return 1
        } else {
            track.stop()
            Log.i(TAG, " return 0")
            return 0
        }
    }

    private fun initAudioTrack() {
        // Phonikud keeps TtsEngine.tts null; fall back to its 22050 Hz sample rate.
        val sampleRate = TtsEngine.tts?.sampleRate() ?: (TtsEngine.phonikud?.sampleRate ?: 22050)
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "sampleRate: $sampleRate, buffLength: $bufLength")

        val attr = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
    }
}
