package com.k2fsa.sherpa.onnx.tts.engine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class BootCompleteReceiver extends BroadcastReceiver {

    private static final String TAG = "BootCompleteReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Log.i(TAG, "Boot completed. Checking pre-load setting...");

        PreferenceHelper preferenceHelper = new PreferenceHelper(context);
        if (preferenceHelper.getPreloadModel()) {
            Log.i(TAG, "Preload enabled, scheduling language preloading...");

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(PreloadLanguageWorker.class).build();
            WorkManager.getInstance(context).enqueue(workRequest);

            Log.i(TAG, "Preload language worker enqueued");
        } else {
            Log.i(TAG, "Preload not enabled");
        }
    }
}

