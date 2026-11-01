package io.github.ramiawar.listenhere;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-device speech to text with Cactus Whistle (bundled 17 MB model, en/de/fr/es/it/nl/pl).
 * The engine keeps one global, non-thread-safe model, so every call runs on a single thread.
 */
final class Whistle {

    interface Callback {
        void onText(String text);
        void onError(String message);
    }

    static {
        System.loadLibrary("whistle_jni");
    }

    private static final ExecutorService ENGINE = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // The engine reads the model in place, so the mapping must live as long as the process.
    private static MappedByteBuffer model;

    private Whistle() {}

    /** Maps and loads the model in the background so it is ready by the time you stop talking. */
    static void preload(Context c) {
        Context app = c.getApplicationContext();
        ENGINE.execute(() -> {
            try {
                ensureLoaded(app);
            } catch (IOException ignored) {
                // Reported by transcribe(), which retries the load.
            }
        });
    }

    /** @param language "en", "nl", ... or null to detect; @param keywords newline-separated words to favour, or null. */
    static void transcribe(Context c, float[] pcm, int samples, String language, String keywords, Callback cb) {
        Context app = c.getApplicationContext();
        ENGINE.execute(() -> {
            try {
                ensureLoaded(app);
                byte[] json = nativeTranscribe(pcm, samples, language, keywords);
                String text = new JSONObject(new String(json, StandardCharsets.UTF_8)).optString("text", "").trim();
                MAIN.post(() -> cb.onText(text));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(String.valueOf(e.getMessage())));
            }
        });
    }

    private static void ensureLoaded(Context c) throws IOException {
        if (model != null) return;
        try (AssetFileDescriptor fd = c.getAssets().openFd("whistle.cact");
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
