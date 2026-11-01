package io.github.ramiawar.listenhere;

/** Turns 16 kHz mono float audio into text. Implementations are called on one thread only. */
interface SpeechEngine {

    /** Loads the model ahead of time, so it's ready by the time you stop talking. */
    void load() throws Exception;

    /**
     * @param language "en", "nl", ... or null to detect
     * @param keywords newline-separated words to favour (names, projects), or null
     */
    String transcribe(float[] pcm, int samples, String language, String keywords) throws Exception;
}
