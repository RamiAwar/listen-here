package dev.inbox.capture;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

/**
 * Records 16 kHz mono float PCM until you stop talking (or tap stop), which is what Whistle takes.
 * End of speech is a plain energy check against an adaptive noise floor; good enough for a
 * sentence dictated into a phone.
 */
final class SpeechRecorder {

    interface Listener {
        /** 0 = background noise, 1 = clearly talking. */
        void onLevel(float level);

        /** The mic is delivering audio: the moment to start talking. */
        void onReady();

        /** @param heardSpeech false when nothing rose above the noise floor. */
        void onDone(float[] pcm, int samples, boolean heardSpeech);

        void onError(String message);
    }

    static final int RATE = 16000;
    private static final int MAX_SECONDS = 30;  // one Whistle pass
    private static final int END_SILENCE_MS = 1500; // room for a breath, not a long think
    private static final int GIVE_UP_MS = 8000; // nothing said at all
    // Silence around the clip: speech models garble a first word that starts at the very first sample.
    private static final int LEAD_PAD = RATE / 2;
    private static final int TAIL_PAD = RATE * 3 / 10;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean stopRequested;
    private Thread thread;

    boolean isRecording() {
        return thread != null && thread.isAlive();
    }

    /** Caller must hold RECORD_AUDIO. */
    void start(Listener l) {
        stopRequested = false;
        thread = new Thread(() -> record(l), "recorder");
        thread.start();
    }

    /** Stops early; whatever was captured is still delivered to onDone. */
    void stop() {
        stopRequested = true;
    }

    @SuppressLint("MissingPermission")
    private void record(Listener l) {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        AudioRecord rec;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, Math.max(min, RATE * 4 / 5));
        } catch (RuntimeException e) {
            main.post(() -> l.onError(e.getMessage()));
            return;
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            main.post(() -> l.onError("microphone unavailable"));
            return;
        }

        float[] all = new float[LEAD_PAD + RATE * MAX_SECONDS + TAIL_PAD];
        int end = all.length - TAIL_PAD;
        float[] chunk = new float[RATE / 20]; // 50 ms: smooth enough for the waveform
        int n = LEAD_PAD, silentMs = 0, speechMs = 0, totalMs = 0;
        float noise = -1, peakRms = 0;
        boolean heard = false;

        rec.startRecording();
        try {
            while (!stopRequested && n < end) {
                int r = rec.read(chunk, 0, Math.min(chunk.length, end - n), AudioRecord.READ_BLOCKING);
                if (r <= 0) break;
                if (n == LEAD_PAD) main.post(l::onReady);
                System.arraycopy(chunk, 0, all, n, r);
                n += r;

                double sum = 0;
                for (int i = 0; i < r; i++) sum += chunk[i] * chunk[i];
                float rms = (float) Math.sqrt(sum / r);
                int ms = r * 1000 / RATE;
                totalMs += ms;
                peakRms = Math.max(peakRms, rms);

                // Noise floor follows the quietest chunks and only creeps up slowly, so pauses
                // between words never drag it up to the level of your voice.
                noise = noise < 0 ? rms : Math.min(rms, noise * 1.01f);
                float threshold = Math.max(noise * 3, 0.0015f);
                if (rms > threshold) {
                    speechMs += ms;
                    silentMs = 0;
                    if (speechMs >= 200) heard = true; // a single click isn't speech
                } else {
                    silentMs += ms;
                    if (!heard) speechMs = 0;
                }
                // Loudness above the noise floor on a log scale, like the ear hears it.
                float level = (float) Math.max(0, Math.min(1, Math.log(rms / Math.max(noise, 1e-5f)) / Math.log(150)));
                main.post(() -> l.onLevel(level));

                if (heard && silentMs >= END_SILENCE_MS) break;
                if (!heard && totalMs >= GIVE_UP_MS) break;
            }
        } finally {
            rec.stop();
            rec.release();
        }
        int samples = n + TAIL_PAD; // the buffer is zeroed, so the tail is already silence
        boolean heardSpeech = heard;
        float gain = normalize(all, samples);
        main.post(() -> l.onDone(all, samples, heardSpeech));
    }

    /** The recognition mic source applies no gain, so speech arrives very quiet; bring it up to a normal level. */
    private static float normalize(float[] pcm, int samples) {
        float max = 0;
        for (int i = 0; i < samples; i++) max = Math.max(max, Math.abs(pcm[i]));
        if (max <= 0) return 1;
        float gain = Math.min(0.6f / max, 40f);
        if (gain <= 1.2f) return 1;
        for (int i = 0; i < samples; i++) pcm[i] *= gain;
        return gain;
    }
}
