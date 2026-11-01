package io.github.ramiawar.listenhere;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs the selected speech model off the main thread. Engines aren't thread-safe, so one thread. */
final class Speech {

    interface Callback {
        void onText(String text);

        void onError(String message);
    }

    static {
        System.loadLibrary("listenhere_jni");
    }

    private static final ExecutorService ENGINE = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Speech() {}

    /** Whether any model is usable; without one, capture sends you to settings. */
    static boolean ready(Context c, Prefs prefs) {
        return Models.selected(c, prefs) != null;
    }

    static void preload(Context c, Prefs prefs) {
        Context app = c.getApplicationContext();
        ENGINE.execute(() -> {
            try {
                SpeechEngine engine = Models.engine(app, prefs);
                if (engine != null) engine.load();
            } catch (Exception ignored) {
                // transcribe() loads again and reports the error.
            }
        });
    }

    static void transcribe(Context c, Prefs prefs, float[] pcm, int samples, Callback cb) {
        Context app = c.getApplicationContext();
        String language = prefs.language();
        String keywords = prefs.keywords();
        ENGINE.execute(() -> {
            try {
                SpeechEngine engine = Models.engine(app, prefs);
                if (engine == null) throw new IllegalStateException(app.getString(R.string.no_model));
                String text = engine.transcribe(pcm, samples, language, keywords);
                MAIN.post(() -> cb.onText(text));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(String.valueOf(e.getMessage())));
            }
        });
    }
}
