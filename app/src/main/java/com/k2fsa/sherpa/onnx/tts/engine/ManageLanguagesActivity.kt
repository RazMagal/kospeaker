package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.onnx.tts.engine.databinding.ActivityManageLanguagesBinding
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ManageLanguagesActivity  : AppCompatActivity() {
    private var binding: ActivityManageLanguagesBinding? = null

    // URIs for selected files
    private var modelFileUri: Uri? = null
    private var tokensFileUri: Uri? = null
    
    // Store lang_code for later use
    private var langCodeForInstallation: String = ""

    private var langCode: String = ""
    private var modelName: String = ""

    // Filterable adapters for the three downloadable-voice lists.
    private var piperAdapter: VoiceAdapter? = null
    private var coquiAdapter: VoiceAdapter? = null
    private var kokoroAdapter: VoiceAdapter? = null

    // Model folders of already-installed voices (from LangDB.Language.folder). A voice
    // is keyed by its unique folder (= downloadable model id), so several voices can
    // share one language and each is marked/blocked independently.
    private var installedFolders: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageLanguagesBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        ThemeUtil.setStatusBarAppearance(this)
        val allPiperModels: Array<String> = resources.getStringArray(R.array.piper_models)
        val allCoquiModels: Array<String> = resources.getStringArray(R.array.coqui_models)
        val allKokoroModels: Array<String> = resources.getStringArray(R.array.kokoro_models)

        // Voices are tracked by their unique model folder (Language.folder), which for
        // downloadable voices equals the model id shown in these lists. We show ALL
        // voices, mark the ones already installed and block re-downloading just those,
        // while still allowing a different voice of the same language to be added.
        val db = LangDB.getInstance(this)
        installedFolders = db.allInstalledLanguages.mapNotNull { it.folder }.toHashSet()

        // Sort alphabetically (case-insensitive) by the displayed model id for scannability.
        val piperSorted = allPiperModels.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val coquiSorted = allCoquiModels.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val kokoroSorted = allKokoroModels.sortedWith(String.CASE_INSENSITIVE_ORDER)

        piperAdapter = VoiceAdapter("vits-piper").also { it.submit(piperSorted) }
        coquiAdapter = VoiceAdapter("vits-coqui").also { it.submit(coquiSorted) }
        kokoroAdapter = VoiceAdapter("kokoro").also { it.submit(kokoroSorted) }

        binding!!.piperModelList.adapter = piperAdapter
        binding!!.piperModelList.setOnItemClickListener { _, _, position, _ ->
            val model = piperAdapter?.getItem(position) ?: return@setOnItemClickListener
            if (isInstalled(model, "vits-piper")) { toastInstalled(); return@setOnItemClickListener }
            val twoLetterCode = model.substring(0, 2)
            val country = model.substring(3, 5)
            val lang = Locale(twoLetterCode).isO3Language
            hideBrowseUi()
            binding!!.downloadSize.setText("")
            Downloader.downloadModels(this, binding, model, lang, country, "vits-piper")
        }

        binding!!.coquiModelList.adapter = coquiAdapter
        binding!!.coquiModelList.setOnItemClickListener { _, _, position, _ ->
            val model = coquiAdapter?.getItem(position) ?: return@setOnItemClickListener
            if (isInstalled(model, "vits-coqui")) { toastInstalled(); return@setOnItemClickListener }
            val twoLetterCode = model.substring(0, 2)
            val country = ""
            val lang = Locale(twoLetterCode).isO3Language
            hideBrowseUi()
            binding!!.downloadSize.setText("")
            Downloader.downloadModels(this, binding, model, lang, country, "vits-coqui")
        }

        binding!!.kokoroModelList.adapter = kokoroAdapter
        binding!!.kokoroModelList.setOnItemClickListener { _, _, position, _ ->
            val model = kokoroAdapter?.getItem(position) ?: return@setOnItemClickListener
            if (isInstalled(model, "kokoro")) { toastInstalled(); return@setOnItemClickListener }
            val twoLetterCode = model.split("-").get(0)
            val country = ""
            val lang = Locale(twoLetterCode).isO3Language
            hideBrowseUi()
            binding!!.downloadSize.setText("")
            Downloader.downloadModels(this, binding, model, lang, country, "kokoro")
        }

        // Filter box + "show installed only" toggle both drive applyFilters().
        binding!!.searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilters() }
        })
        binding!!.showInstalledOnly.setOnCheckedChangeListener { _, _ -> applyFilters() }
        // Hebrew isn't a downloadable Piper/Kokoro voice; the hint (shown by the
        // filter) opens the same Install-from-SD dialog, which has MMS/Phonikud radios.
        binding!!.hebrewHint.setOnClickListener { sdInstall() }
        applyFilters()
    }

    /** Re-run the text + installed-only filters on all three lists and toggle the empty state. */
    private fun applyFilters() {
        val b = binding ?: return
        val query = b.searchBox.text?.toString() ?: ""
        val installedOnly = b.showInstalledOnly.isChecked
        piperAdapter?.filter(query, installedOnly)
        coquiAdapter?.filter(query, installedOnly)
        kokoroAdapter?.filter(query, installedOnly)
        // Hebrew has no downloadable voice, so surface the offline hint when the
        // query matches Hebrew and suppress the "no matching languages" empty state.
        val hebrew = matchesHebrew(query)
        b.hebrewHint.visibility = if (hebrew) View.VISIBLE else View.GONE
        val empty = (piperAdapter?.count ?: 0) == 0 &&
            (coquiAdapter?.count ?: 0) == 0 &&
            (kokoroAdapter?.count ?: 0) == 0
        b.noResults.visibility = if (empty && !hebrew) View.VISIBLE else View.GONE
    }

    /**
     * True when the filter query refers to Hebrew: the (lowercased) query is a
     * substring of "hebrew" (covers "he"/"heb"/"hebr"...), or it involves the
     * Hebrew word "עברית". Empty queries never match.
     */
    private fun matchesHebrew(query: String): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return false
        return "hebrew".contains(q) || q.contains("עברית") || "עברית".contains(q)
    }

    /** Hide the whole browse UI once a download starts (matches the pre-existing behaviour). */
    private fun hideBrowseUi() {
        val b = binding ?: return
        b.searchBox.visibility = View.GONE
        b.showInstalledOnly.visibility = View.GONE
        b.hebrewHint.visibility = View.GONE
        b.noResults.visibility = View.GONE
        b.buttonTestVoices.visibility = View.GONE
        b.piperHeader.visibility = View.GONE
        b.coquiHeader.visibility = View.GONE
        b.kokoroHeader.visibility = View.GONE
        b.piperModelList.visibility = View.GONE
        b.coquiModelList.visibility = View.GONE
        b.kokoroModelList.visibility = View.GONE
    }

    private fun toastInstalled() {
        Toast.makeText(this, R.string.language_already_installed, Toast.LENGTH_SHORT).show()
    }

    // Best-effort language-code derivation, mirroring the download click handlers:
    // piper/coqui take the first two chars, kokoro the part before the first "-".
    private fun code2Of(model: String, type: String): String {
        return if (type == "kokoro") model.substringBefore("-")
        else if (model.length >= 2) model.substring(0, 2) else model
    }

    private fun iso3Of(code2: String): String {
        return try { if (code2.isEmpty()) "" else Locale(code2).isO3Language } catch (e: Exception) { "" }
    }

    // A voice is "installed" when a DB row already uses its folder. Downloaded voices
    // store folder == model id, so this marks/blocks exactly that one voice and leaves
    // the other voices of the same language downloadable.
    private fun isInstalled(model: String, type: String): Boolean {
        return installedFolders.contains(model)
    }

    // Text used for filtering: model id + display language name + 2-letter + ISO-639-3 codes.
    private fun searchableText(model: String, type: String): String {
        val code2 = code2Of(model, type)
        val name = try { Locale(code2).displayLanguage } catch (e: Exception) { "" }
        return (model + " " + name + " " + code2 + " " + iso3Of(code2)).lowercase(Locale.ROOT)
    }

    /** ArrayAdapter that keeps the full list and shows a filtered view + installed marker. */
    private inner class VoiceAdapter(private val type: String) :
        ArrayAdapter<String>(this@ManageLanguagesActivity, R.layout.list_item, R.id.text_view) {

        private val full = ArrayList<String>()
        private var query = ""
        private var installedOnly = false

        fun submit(items: List<String>) {
            full.clear()
            full.addAll(items)
            apply()
        }

        fun filter(q: String, onlyInstalled: Boolean) {
            query = q.trim().lowercase(Locale.ROOT)
            installedOnly = onlyInstalled
            apply()
        }

        private fun apply() {
            val result = full.filter { model ->
                (!installedOnly || isInstalled(model, type)) &&
                    (query.isEmpty() || searchableText(model, type).contains(query))
            }
            setNotifyOnChange(false)
            clear()
            addAll(result)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val model = getItem(position)
            if (model != null && isInstalled(model, type)) {
                view.findViewById<TextView>(R.id.text_view).text =
                    getString(R.string.installed_prefix, model)
            }
            return view
        }
    }

    fun startMain(view: View) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    fun testVoices(view: View) {startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/spaces/k2-fsa/text-to-speech/")))}
    
    fun installFromSD(view: View) {
        sdInstall()
    }
    fun sdInstall() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_install_custom_model, null)

        val langInput = dialogView.findViewById<EditText>(R.id.editTextLangCode)
        val modelInput = dialogView.findViewById<EditText>(R.id.editTextModelName)
        val selectModelBtn = dialogView.findViewById<Button>(R.id.buttonSelectModel)
        val selectTokensBtn = dialogView.findViewById<Button>(R.id.buttonSelectTokens)
        val modelTypeGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupModelType)
        val installBtn = dialogView.findViewById<Button>(R.id.buttonInstall)

        // Initialize button text/visibility 
        selectModelBtn.setOnClickListener {
            modelPickerLauncher.launch(arrayOf("application/octet-stream"))
        }

        selectTokensBtn.setOnClickListener {
            tokensPickerLauncher.launch(arrayOf("text/plain"))
        }

        installBtn.setOnClickListener {
            langCode = langInput.text.toString().trim()
            modelName = modelInput.text.toString().trim()

            // Validate
            if (langCode.length != 3) {
                Toast.makeText(this, R.string.language_code_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (modelName.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_model_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Several voices may share a language now, so per-voice de-duplication is
            // handled in the install helpers (by unique folder), not by language code.

            // Model architecture chosen by the user (defaults to Piper).
            // MMS models (facebook/mms-tts-*) must be stored as "vits-mms" so
            // TtsEngine loads them with the character frontend (empty dataDir
            // and lexicon) instead of the espeak frontend used for Piper.
            // Phonikud (premium Hebrew) uses three large ONNX files pushed via
            // ADB/file-manager, so it registers the entry without the file pickers.
            val type = when (modelTypeGroup.checkedRadioButtonId) {
                R.id.radioModelTypeMms -> "vits-mms"
                R.id.radioModelTypePhonikud -> "phonikud"
                else -> "vits-piper"
            }

            if (type == "phonikud") {
                installPhonikudModel(langCode)
                return@setOnClickListener
            }

            if (modelFileUri == null) {
                Toast.makeText(this, getString(R.string.select_model_file), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (tokensFileUri == null) {
                Toast.makeText(this, getString(R.string.select_tokens_file), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Proceed with install
            installCustomModel(langCode, modelFileUri!!, tokensFileUri!!, type)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.install_from_sd))
            .setView(dialogView)
            .setNegativeButton(getString(android.R.string.cancel)) { dialogInterface, _ -> dialogInterface.dismiss() }
            .create()

        dialog.show()
    }

    /**
     * Register a premium Hebrew (phonikud) model. The three ONNX files are far too large
     * (~308MB + ~64MB) to copy through the SAF file pickers, so this only creates the model
     * folder and DB row; the user pushes phonikud.onnx, tokenizer.json and shaul.onnx into the
     * folder via ADB or a file manager. See docs/HEBREW.md.
     */
    private fun installPhonikudModel(langCode: String) {
        // Phonikud's ~370MB files are pushed by hand into files/<langCode>/, so the
        // folder is fixed to the language code. Block only an exact re-install.
        val db = LangDB.getInstance(this)
        if (db.allInstalledLanguages.any { it.folder == langCode }) {
            Toast.makeText(this, R.string.language_already_installed, Toast.LENGTH_SHORT).show()
            return
        }
        val directory = File(this.getExternalFilesDir(null), "/$langCode/")
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, R.string.error_copying_files, Toast.LENGTH_SHORT).show()
            return
        }

        db.addLanguage(modelName, langCode, "", 0, 1.0f, 1.0f, "phonikud", langCode)

        Toast.makeText(
            this,
            getString(R.string.phonikud_push_files, langCode, directory.absolutePath),
            Toast.LENGTH_LONG,
        ).show()

        val preferenceHelper = PreferenceHelper(this)
        preferenceHelper.setCurrentLanguage(langCode)
        preferenceHelper.setActiveVoiceFolder(langCode, langCode)
        modelFileUri = null
        tokensFileUri = null
        langCodeForInstallation = ""
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    /**
     * Filesystem-safe, collision-free folder name for a hand-installed voice, derived
     * from "<langCode>-<modelName>" and suffixed if that folder is already taken so a
     * second custom voice for the same language does not overwrite the first.
     */
    private fun uniqueFolder(langCode: String, name: String): String {
        val base = "$langCode-$name".replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { langCode }
        val existing = LangDB.getInstance(this).allInstalledLanguages.mapNotNull { it.folder }.toHashSet()
        if (!existing.contains(base)) return base
        var i = 2
        while (existing.contains("${base}_$i")) i++
        return "${base}_$i"
    }

    private fun installCustomModel(langCode: String, modelUri: Uri, tokensUri: Uri, type: String) {
        // Each voice gets its own unique folder (country code is empty as requested).
        val folder = uniqueFolder(langCode, modelName)
        val directory = File(this.getExternalFilesDir(null), "/$folder/")
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, R.string.error_copying_files, Toast.LENGTH_SHORT).show()
            return
        }

        // Copy model.onnx file
        val modelDest = File(directory, Downloader.onnxModel)
        try {
            copyFile(modelUri, modelDest)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_copying_files, Toast.LENGTH_SHORT).show()
            return
        }

        // Copy tokens.txt file
        val tokensDest = File(directory, Downloader.tokens)
        try {
            copyFile(tokensUri, tokensDest)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_copying_files, Toast.LENGTH_SHORT).show()
            return
        }

        // Add to database with the chosen model type, empty country code and unique folder
        val db = LangDB.getInstance(this)
        db.addLanguage(modelName, langCode, "", 0, 1.0f, 1.0f, type, folder)

        // Show success message
        Toast.makeText(this, "+ \"$langCode\" = \"$modelName\" ", Toast.LENGTH_SHORT).show()
        val preferenceHelper = PreferenceHelper(this)
        preferenceHelper.setCurrentLanguage(langCode)
        preferenceHelper.setActiveVoiceFolder(langCode, folder)
        // Reset file URIs and lang code for next use
        modelFileUri = null
        tokensFileUri = null
        langCodeForInstallation = ""
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }
    
    private fun copyFile(sourceUri: Uri, destFile: File) {
        this.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    // Register for file pickers 
    private val modelPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            modelFileUri = uri
        }
    }

    private val tokensPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            tokensFileUri = uri
        }
    }
}
