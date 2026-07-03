@file:OptIn(ExperimentalMaterial3Api::class)

package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Guided onboarding screen: "Set up with KOReader".
 *
 * Walks a Boox user through the fiddly parts of using KoSpeaker as their
 * device-wide TTS voice inside KOReader. Intentionally self-contained: it does
 * NOT touch the engine, the reading pipeline or the model/download logic.
 *
 * Built with Compose (like MainActivity) so no extra layout XML is needed.
 */
class SetupActivity : ComponentActivity() {

    // A TextToSpeech client bound to the user's *default* engine. Used both to
    // detect whether KoSpeaker is the default and to speak the test sample.
    // Released in onDestroy().
    private var tts: TextToSpeech? = null

    // Set once the TTS client has finished initialising. @Volatile because the
    // init callback runs on a binder thread while we read it on the UI thread.
    @Volatile
    private var ttsReady = false

    // Whether KoSpeaker is the current default TTS engine.
    //   null  -> unknown / detection failed  => status line is hidden
    //   true  -> KoSpeaker is default
    //   false -> some other engine is default
    private val isDefaultEngine = mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.setStatusBarAppearance(this)

        // Bind to the user's default engine. When KoSpeaker is the default this
        // simply binds back to our own TtsService, which is fine.
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                refreshDefaultEngineStatus()
            }
        }

        setContent { SetupScreen() }
    }

    /** Re-check on resume so the status line reflects changes made in Settings. */
    override fun onResume() {
        super.onResume()
        if (ttsReady) refreshDefaultEngineStatus()
    }

    /** Compare the system default engine to our applicationId. Robust to failure. */
    private fun refreshDefaultEngineStatus() {
        isDefaultEngine.value = try {
            tts?.defaultEngine == BuildConfig.APPLICATION_ID
        } catch (e: Exception) {
            null // detection failed -> hide the status line rather than lie
        }
    }

    /**
     * Open the Android system text-to-speech settings so the user can pick
     * KoSpeaker as the default engine and adjust speed/pitch. Some Boox ROMs
     * lack the dedicated screen, so fall back to the general Settings root.
     */
    private fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, R.string.setup_settings_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Speak a short sample through the default engine. Best-effort. */
    private fun speakTest() {
        val engine = tts
        if (engine == null || !ttsReady) {
            Toast.makeText(this, R.string.setup_voice_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        // Setting a language is best-effort: some engines reject it, so ignore
        // the result and just try to speak.
        try {
            engine.language = Locale.getDefault()
        } catch (e: Exception) {
            // ignore - fall through and attempt to speak anyway
        }
        engine.speak(
            getString(R.string.setup_test_sample),
            TextToSpeech.QUEUE_FLUSH,
            null,
            "kospeaker_setup_test"
        )
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.setup_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    @Composable
    private fun SetupScreen() {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.setup_title)) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    Icons.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.setup_back)
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // ---- Step a: open the system TTS settings --------------------
                    SectionTitle(stringResource(R.string.setup_section_default))
                    Text(stringResource(R.string.setup_tts_settings_hint))
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = stringResource(R.string.setup_open_tts_settings),
                        onClick = { openTtsSettings() }
                    )

                    // ---- Step b: live default-engine status ----------------------
                    val default = isDefaultEngine.value
                    if (default != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (default) stringResource(R.string.setup_status_default)
                            else stringResource(R.string.setup_status_not_default),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // ---- Step c: KOReader instructions ---------------------------
                    SectionTitle(stringResource(R.string.setup_section_koreader))
                    Text(stringResource(R.string.setup_step_1))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.setup_plugin_url),
                        color = colorResource(R.color.primaryDark),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            openUrl(getString(R.string.setup_plugin_url))
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    // Emphasised note: the plugin must stay installed.
                    Text(
                        text = stringResource(R.string.setup_keep_plugin_note),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.setup_step_2))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.setup_step_3))

                    Spacer(Modifier.height(20.dp))

                    // ---- Step d: optional test voice -----------------------------
                    PrimaryButton(
                        text = stringResource(R.string.setup_test_voice),
                        onClick = { speakTest() }
                    )
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }

    @Composable
    private fun PrimaryButton(text: String, onClick: () -> Unit) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.primaryDark),
                contentColor = colorResource(R.color.white)
            ),
            onClick = onClick
        ) {
            Text(text)
        }
    }
}
