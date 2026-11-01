package io.github.ramiawar.listenhere;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** whisper.cpp, built from source. Models are imported by the user (see {@link Models}). */
final class Whisper implements SpeechEngine {

    private static String loaded; // path of the model whisper.cpp holds; one at a time
    private final Models.Model model;
    private final File file;

    Whisper(Models.Model model, File file) {
        this.model = model;
        this.file = file;
    }

    @Override
    public void load() throws IOException {
        if (file.getPath().equals(loaded)) return;
        String err = nativeLoad(file.getPath());
        if (err != null) throw new IOException(err);
        loaded = file.getPath();
    }

    @Override
    public String transcribe(float[] pcm, int samples, String language, String keywords) throws Exception {
        load();
        String lang = model.englishOnly ? "en" : language;
        String prompt = keywords == null ? null : keywords.replace('\n', ',').replace(",", ", ");
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        byte[] text = nativeTranscribe(pcm, samples, lang, prompt, threads);
        return new String(text, StandardCharsets.UTF_8).trim();
    }

    /** @return null on success, else why the model didn't load. */
    private static native String nativeLoad(String path);

    private static native byte[] nativeTranscribe(float[] pcm, int samples, String language, String prompt, int threads);
}
