package com.k2fsa.sherpa.onnx.tts.engine;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class PreloadLanguageWorker extends Worker {

    private static final String TAG = "PreloadLanguageWorker";

    public PreloadLanguageWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        PreferenceHelper preferenceHelper = new PreferenceHelper(getApplicationContext());
        String language = preferenceHelper.getCurrentLanguage();
        if (language == null || language.isEmpty()) {
            Log.w(TAG, "No language selected, skipping preload");
            return Result.success();
        }

        Log.i(TAG, "Preloading language: " + language);

        try {
            // Preload the engine
            if (TtsEngine.getAvailableLanguages(getApplicationContext()).contains(language)) {
                TtsEngine.createTts(getApplicationContext(), language);
                Log.i(TAG, "Language " + language + " preloaded successfully");
            } else {
                Log.w(TAG, "Language " + language + " not found in available languages");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to preload language " + language, e);
            return Result.failure();
        }

        return Result.success();
    }
}

