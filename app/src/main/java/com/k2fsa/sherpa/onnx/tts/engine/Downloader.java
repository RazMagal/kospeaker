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
    static long onnxModelDownloadSize = 0L;
    static long tokensDownloadSize = 0L;
    static long voicesDownloadSize = 0L;
    static boolean onnxModelFinished = false;
    static boolean tokensFinished = false;
    static boolean voicesFinished = false;
    static int onnxModelSize = 0;
    static int tokensSize = 0;
    static int voicesSize = 0;

    public static void downloadModels(final Activity activity, ActivityManageLanguagesBinding binding, String model, String lang, String country, String type) {
        String modelName="";
        if (type.equals("vits-piper")) modelName = model + ".onnx";
        else if (type.equals("vits-coqui")) modelName = "model.onnx";
        else if (type.startsWith("kokoro")) modelName = "model.onnx";

        // Kokoro models additionally require a voices.bin file. All three files
        // (model.onnx, voices.bin, tokens.txt) live at the repo root, e.g.
        // https://huggingface.co/csukuangfj/kokoro-en-v0_19/resolve/main/...
        final boolean isKokoro = type.startsWith("kokoro");
        // For non-Kokoro models there is no voices file, so treat it as done.
        voicesFinished = !isKokoro;

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
            onnxModelFinished = false;
            Log.d("TTS Engine", "onnx model file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url;

                    url = new URL(onnxModelUrl);

                    Log.d("TTS Engine", "Download model");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);
                    onnxModelSize = ucon.getContentLength();

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    File tempOnnxFile = new File(activity.getExternalFilesDir(null)+ "/" + folder + "/" + "model.tmp");
                    if (tempOnnxFile.exists()) tempOnnxFile.delete();

                    FileOutputStream outStream = new FileOutputStream(tempOnnxFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (tempOnnxFile.exists()) onnxModelDownloadSize = tempOnnxFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((tokensDownloadSize + onnxModelDownloadSize + voicesDownloadSize)/1024/1024 + " MB / " + (onnxModelSize + tokensSize + voicesSize)/1024/1024 + " MB");
                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    if (!tempOnnxFile.exists()) {
                        throw new IOException();
                    }

                    tempOnnxFile.renameTo(onnxModelFile);
                    onnxModelFinished = true;
                    activity.runOnUiThread(() -> {
                        if (tokensFinished && onnxModelFinished && voicesFinished && binding.buttonStart.getVisibility()==View.GONE){
                            binding.buttonStart.setVisibility(View.VISIBLE);
                            PreferenceHelper preferenceHelper = new PreferenceHelper(activity);
                            preferenceHelper.setCurrentLanguage(lang);
                            preferenceHelper.setActiveVoiceFolder(lang, folder);
                            LangDB langDB = LangDB.getInstance(activity);
                            langDB.addLanguage(model, lang, country, 0, 1.0f, 1.0f, type, folder);
                        }
                    });
                } catch (IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    onnxModelFile.delete();
                    Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        }

        File tokensFile = new File(activity.getExternalFilesDir(null) + "/" + folder + "/" + tokens);
        if (tokensFile.exists()) tokensFile.delete();
        if (!tokensFile.exists()) {
            tokensFinished = false;
            Log.d("TTS Engine", "tokens file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url = new URL(tokensUrl);
                    Log.d("TTS Engine", "Download tokens file");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);
                    tokensSize = ucon.getContentLength();

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    File tempTokensFile = new File(activity.getExternalFilesDir(null)+ "/" + folder + "/" + "tokens.tmp");
                    if (tempTokensFile.exists()) tempTokensFile.delete();

                    FileOutputStream outStream = new FileOutputStream(tempTokensFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (tempTokensFile.exists()) tokensDownloadSize = tempTokensFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((tokensDownloadSize + onnxModelDownloadSize + voicesDownloadSize)/1024/1024 + " MB / " + (onnxModelSize + tokensSize + voicesSize)/1024/1024 + " MB");
                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    if (!tempTokensFile.exists()) {
                        throw new IOException();
                    }

                    tempTokensFile.renameTo(tokensFile);
                    tokensFinished = true;
                    activity.runOnUiThread(() -> {
                        if (tokensFinished && onnxModelFinished && voicesFinished && binding.buttonStart.getVisibility()==View.GONE){
                            binding.buttonStart.setVisibility(View.VISIBLE);
                            PreferenceHelper preferenceHelper = new PreferenceHelper(activity);
                            preferenceHelper.setCurrentLanguage(lang);
                            preferenceHelper.setActiveVoiceFolder(lang, folder);
                            LangDB langDB = LangDB.getInstance(activity);
                            langDB.addLanguage(model, lang, country, 0, 1.0f, 1.0f, type, folder);
                        }
                    });

                } catch (IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    tokensFile.delete();
                    Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        }

        // Kokoro-only: download voices.bin (mirrors the tokens download above).
        if (isKokoro) {
            File voicesFileHandle = new File(activity.getExternalFilesDir(null) + "/" + folder + "/" + voices);
            if (voicesFileHandle.exists()) voicesFileHandle.delete();
            if (!voicesFileHandle.exists()) {
                voicesFinished = false;
                Log.d("TTS Engine", "voices file does not exist");
                Thread thread = new Thread(() -> {
                    try {
                        URL url = new URL(voicesUrl);
                        Log.d("TTS Engine", "Download voices file");

                        URLConnection ucon = url.openConnection();
                        ucon.setReadTimeout(5000);
                        ucon.setConnectTimeout(10000);
                        voicesSize = ucon.getContentLength();

                        InputStream is = ucon.getInputStream();
                        BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                        File tempVoicesFile = new File(activity.getExternalFilesDir(null) + "/" + folder + "/" + "voices.tmp");
                        if (tempVoicesFile.exists()) tempVoicesFile.delete();

                        FileOutputStream outStream = new FileOutputStream(tempVoicesFile);
                        byte[] buff = new byte[5 * 1024];

                        int len;
                        while ((len = inStream.read(buff)) != -1) {
                            outStream.write(buff, 0, len);
                            if (tempVoicesFile.exists()) voicesDownloadSize = tempVoicesFile.length();
                            activity.runOnUiThread(() -> {
                                binding.downloadSize.setText((tokensDownloadSize + onnxModelDownloadSize + voicesDownloadSize)/1024/1024 + " MB / " + (onnxModelSize + tokensSize + voicesSize)/1024/1024 + " MB");
                            });
                        }
                        outStream.flush();
                        outStream.close();
                        inStream.close();

                        if (!tempVoicesFile.exists()) {
                            throw new IOException();
                        }

                        tempVoicesFile.renameTo(voicesFileHandle);
                        voicesFinished = true;
                        activity.runOnUiThread(() -> {
                            if (tokensFinished && onnxModelFinished && voicesFinished && binding.buttonStart.getVisibility()==View.GONE){
                                binding.buttonStart.setVisibility(View.VISIBLE);
                                PreferenceHelper preferenceHelper = new PreferenceHelper(activity);
                                preferenceHelper.setCurrentLanguage(lang);
                                preferenceHelper.setActiveVoiceFolder(lang, folder);
                                LangDB langDB = LangDB.getInstance(activity);
                                langDB.addLanguage(model, lang, country, 0, 1.0f, 1.0f, type, folder);
                            }
                        });

                    } catch (IOException i) {
                        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                        voicesFileHandle.delete();
                        Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
                    }
                });
                thread.start();
            }
        }
    }
}