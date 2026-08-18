package com.k2fsa.sherpa.onnx.tts.engine;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import com.k2fsa.sherpa.onnx.tts.engine.databinding.ActivityManageLanguagesBinding;


@SuppressWarnings("ResultOfMethodCallIgnored")
public class Downloader {
    static final String onnxModel = "model.onnx";
    static final String tokens = "tokens.txt";
    static final String voices = "voices.bin"; // Kokoro speaker embeddings

    // Per-download state. One instance is created per downloadModels() call so that
    // back-to-back downloads (start voice A, go Back, start voice B) can never mix
    // their completion flags / byte counters: with the previous static fields, A's
    // completions could satisfy B's "all three finished" guard and register a row
    // for B while B's model.onnx was still half-downloaded.
    private static final class State {
        long onnxModelDownloadSize = 0L;
        long tokensDownloadSize = 0L;
        long voicesDownloadSize = 0L;
        boolean onnxModelFinished = false;
        boolean tokensFinished = false;
        boolean voicesFinished = false;
        int onnxModelSize = 0;
        int tokensSize = 0;
        int voicesSize = 0;
        // The DB row must be inserted exactly once, by whichever completion callback
        // (all run on the main looper) observes all three parts finished first.
        boolean registered = false;
    }

    public static void downloadModels(final Activity activity, ActivityManageLanguagesBinding binding, String model, String lang, String country, String type) {
        String modelName="";
        if (type.equals("vits-piper")) modelName = model + ".onnx";
        else if (type.equals("vits-coqui")) modelName = "model.onnx";
        else if (type.startsWith("kokoro")) modelName = "model.onnx";

        // Kokoro models additionally require a voices.bin file. All three files
        // (model.onnx, voices.bin, tokens.txt) live at the repo root, e.g.
        // https://huggingface.co/csukuangfj/kokoro-en-v0_19/resolve/main/...
        final boolean isKokoro = type.startsWith("kokoro");
        final State st = new State();
        // For non-Kokoro models there is no voices file, so treat it as done.
        st.voicesFinished = !isKokoro;

        String onnxModelUrl = "https://huggingface.co/csukuangfj/"+ type + "-" + model + "/resolve/main/" + modelName;
        String tokensUrl = "https://huggingface.co/csukuangfj/" + type + "-" + model + "/resolve/main/tokens.txt";
        String voicesUrl = "https://huggingface.co/csukuangfj/" + type + "-" + model + "/resolve/main/" + voices;

        // Each voice gets its own directory keyed by the unique model id, so a second
        // voice for an already-installed language no longer overwrites the first.
        final String folder = model;

        File directory = new File(activity.getExternalFilesDir(null)+ "/" + folder + "/");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e("TTS Engine", "Failed to make directory: " + directory);
            return;
        }

        activity.runOnUiThread(() -> binding.downloadSize.setVisibility(View.VISIBLE));

        File onnxModelFile = new File(activity.getExternalFilesDir(null)+ "/" + folder + "/" + onnxModel);
        if (onnxModelFile.exists()) onnxModelFile.delete();
        if (!onnxModelFile.exists()) {
            st.onnxModelFinished = false;
            Log.d("TTS Engine", "onnx model file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url;

                    url = new URL(onnxModelUrl);

                    Log.d("TTS Engine", "Download model");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);
                    st.onnxModelSize = ucon.getContentLength();

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    File tempOnnxFile = new File(activity.getExternalFilesDir(null)+ "/" + folder + "/" + "model.tmp");
                    if (tempOnnxFile.exists()) tempOnnxFile.delete();

                    FileOutputStream outStream = new FileOutputStream(tempOnnxFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (tempOnnxFile.exists()) st.onnxModelDownloadSize = tempOnnxFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((st.tokensDownloadSize + st.onnxModelDownloadSize + st.voicesDownloadSize)/1024/1024 + " MB / " + (st.onnxModelSize + st.tokensSize + st.voicesSize)/1024/1024 + " MB");
                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    if (!tempOnnxFile.exists()) {
                        throw new IOException();
                    }

                    tempOnnxFile.renameTo(onnxModelFile);
                    st.onnxModelFinished = true;
                    activity.runOnUiThread(() -> registerIfComplete(activity, binding, st, model, lang, country, type, folder));
                } catch (IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    onnxModelFile.delete();
                    Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        } else {
            // The old file could not be deleted but is still a complete model:
            // treat this part as finished so registration cannot wedge forever.
            st.onnxModelFinished = true;
            activity.runOnUiThread(() -> registerIfComplete(activity, binding, st, model, lang, country, type, folder));
        }

        File tokensFile = new File(activity.getExternalFilesDir(null) + "/" + folder + "/" + tokens);
        if (tokensFile.exists()) tokensFile.delete();
        if (!tokensFile.exists()) {
            st.tokensFinished = false;
            Log.d("TTS Engine", "tokens file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url = new URL(tokensUrl);
                    Log.d("TTS Engine", "Download tokens file");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);
                    st.tokensSize = ucon.getContentLength();

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    File tempTokensFile = new File(activity.getExternalFilesDir(null)+ "/" + folder + "/" + "tokens.tmp");
                    if (tempTokensFile.exists()) tempTokensFile.delete();

                    FileOutputStream outStream = new FileOutputStream(tempTokensFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (tempTokensFile.exists()) st.tokensDownloadSize = tempTokensFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((st.tokensDownloadSize + st.onnxModelDownloadSize + st.voicesDownloadSize)/1024/1024 + " MB / " + (st.onnxModelSize + st.tokensSize + st.voicesSize)/1024/1024 + " MB");
                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    if (!tempTokensFile.exists()) {
                        throw new IOException();
                    }

                    tempTokensFile.renameTo(tokensFile);
                    st.tokensFinished = true;
                    activity.runOnUiThread(() -> registerIfComplete(activity, binding, st, model, lang, country, type, folder));

                } catch (IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    tokensFile.delete();
                    Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        } else {
            st.tokensFinished = true;
            activity.runOnUiThread(() -> registerIfComplete(activity, binding, st, model, lang, country, type, folder));
        }

        // Kokoro-only: download voices.bin (mirrors the tokens download above).
        if (isKokoro) {
            File voicesFileHandle = new File(activity.getExternalFilesDir(null) + "/" + folder + "/" + voices);
            if (voicesFileHandle.exists()) voicesFileHandle.delete();
            if (!voicesFileHandle.exists()) {
                st.voicesFinished = false;
                Log.d("TTS Engine", "voices file does not exist");
                Thread thread = new Thread(() -> {
                    try {
                        URL url = new URL(voicesUrl);
                        Log.d("TTS Engine", "Download voices file");

                        URLConnection ucon = url.openConnection();
                        ucon.setReadTimeout(5000);
                        ucon.setConnectTimeout(10000);
                        st.voicesSize = ucon.getContentLength();

                        InputStream is = ucon.getInputStream();
                        BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                        File tempVoicesFile = new File(activity.getExternalFilesDir(null) + "/" + folder + "/" + "voices.tmp");
                        if (tempVoicesFile.exists()) tempVoicesFile.delete();

                        FileOutputStream outStream = new FileOutputStream(tempVoicesFile);
                        byte[] buff = new byte[5 * 1024];

                        int len;
                        while ((len = inStream.read(buff)) != -1) {
                            outStream.write(buff, 0, len);
                            if (tempVoicesFile.exists()) st.voicesDownloadSize = tempVoicesFile.length();
                            activity.runOnUiThread(() -> {
                                binding.downloadSize.setText((st.tokensDownloadSize + st.onnxModelDownloadSize + st.voicesDownloadSize)/1024/1024 + " MB / " + (st.onnxModelSize + st.tokensSize + st.voicesSize)/1024/1024 + " MB");
                            });
                        }
                        outStream.flush();
                        outStream.close();
                        inStream.close();

                        if (!tempVoicesFile.exists()) {
                            throw new IOException();
                        }

                        tempVoicesFile.renameTo(voicesFileHandle);
                        st.voicesFinished = true;
                        activity.runOnUiThread(() -> registerIfComplete(activity, binding, st, model, lang, country, type, folder));

                    } catch (IOException i) {
                        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                        voicesFileHandle.delete();
                        Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
                    }
                });
                thread.start();
            } else {
                st.voicesFinished = true;
                activity.runOnUiThread(() -> registerIfComplete(activity, binding, st, model, lang, country, type, folder));
            }
        }
    }

    // Runs on the main looper. Registers the voice exactly once, when all parts of
    // THIS download (tracked in its own State) have finished.
    private static void registerIfComplete(Activity activity, ActivityManageLanguagesBinding binding, State st, String model, String lang, String country, String type, String folder) {
        if (st.registered || !st.tokensFinished || !st.onnxModelFinished || !st.voicesFinished) return;
        st.registered = true;
        binding.buttonStart.setVisibility(View.VISIBLE);
        PreferenceHelper preferenceHelper = new PreferenceHelper(activity);
        preferenceHelper.setCurrentLanguage(lang);
        preferenceHelper.setActiveVoiceFolder(lang, folder);
        LangDB langDB = LangDB.getInstance(activity);
        langDB.addLanguage(model, lang, country, 0, 1.0f, 1.0f, type, folder);
    }
}
