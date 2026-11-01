package io.github.ramiawar.listenhere;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/** Cactus Whistle: the bundled 17 MB model (en/de/fr/es/it/nl/pl). Prebuilt engine, so not in the F-Droid build. */
final class Whistle implements SpeechEngine {

    // The engine reads the model in place, so the mapping must live as long as the process.
    private static MappedByteBuffer model;
    private final Context app;

    Whistle(Context app) {
        this.app = app;
    }

    @Override
    public void load() throws IOException {
        ensureLoaded();
    }

    @Override
    public String transcribe(float[] pcm, int samples, String language, String keywords) throws Exception {
        ensureLoaded();
        byte[] json = nativeTranscribe(pcm, samples, language, keywords);
        return new JSONObject(new String(json, StandardCharsets.UTF_8)).optString("text", "").trim();
    }

    private void ensureLoaded() throws IOException {
        if (model != null) return;
        try (AssetFileDescriptor fd = app.getAssets().openFd("whistle.cact");
             FileInputStream in = fd.createInputStream()) {
            MappedByteBuffer buf = in.getChannel()
                    .map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getLength());
            String err = nativeLoad(buf);
            if (err != null) throw new IOException(err);
            model = buf;
        }
    }

    private static native String nativeLoad(java.nio.ByteBuffer model);

    /** Raw UTF-8 bytes: JNI's NewStringUTF aborts the process on 4-byte characters such as emoji. */
    private static native byte[] nativeTranscribe(float[] pcm, int samples, String language, String keywords);
}
