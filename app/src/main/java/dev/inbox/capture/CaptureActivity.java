package dev.inbox.capture;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** The whole app, from the user's point of view: listen, show the line, append it to the inbox. */
public class CaptureActivity extends Activity {

    private static final int REQ_MIC = 1;
    private static final int REQ_NOTIFY = 2;

    private Prefs prefs;
    /** What was said (or shared) so far; dictating again adds to it. */
    private String text = "";
    private VoiceView wave;
    private TextView status;
    private TextView title;
    private TextView due;
    private View mic;
    private View settings;
    private View saveButton;
    private KeyguardManager keyguard;
    private final SpeechRecorder recorder = new SpeechRecorder();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        if (!prefs.configured()) {
            Toast.makeText(this, R.string.setup_first, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.capture);
        getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        getWindow().setGravity(Gravity.CENTER);

        wave = findViewById(R.id.wave);
        status = findViewById(R.id.status);
        title = findViewById(R.id.title);
        due = findViewById(R.id.due);
        mic = findViewById(R.id.mic);
        settings = findViewById(R.id.settings);
        keyguard = getSystemService(KeyguardManager.class);
        mic.setOnClickListener(v -> {
            if (recorder.isRecording()) recorder.stop();
            else listen();
        });
        saveButton = findViewById(R.id.save);
        saveButton.setOnClickListener(v -> save());
        settings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        Whistle.preload(this);
        if (state == null) start(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        start(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ask while unlocked, so the lock-screen queue can show its notification later.
        if (!keyguard.isKeyguardLocked()
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !prefs.sp.getBoolean("asked_notifications", false)) {
            prefs.sp.edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
        // Over the lock screen you can add to the inbox, nothing else.
        settings.setVisibility(keyguard.isKeyguardLocked() ? View.INVISIBLE : View.VISIBLE); // keeps the mic centred
    }

    @Override
    protected void onStop() {
        super.onStop();
        recorder.stop();
    }

    private void start(Intent intent) {
        String shared = Intent.ACTION_SEND.equals(intent.getAction())
                ? intent.getStringExtra(Intent.EXTRA_TEXT) : null;
        if (shared != null) {
            setText(shared.trim());
        } else if (prefs.autoMic()) {
            listen();
        }
    }

    private void setText(String t) {
        text = t;
        TaskParser.Task first = null;
        int count = 0;
        for (String line : text.split("\n")) {
            TaskParser.Task task = TaskParser.parse(line, LocalDateTime.now());
            if (task.title.isEmpty()) continue;
            if (first == null) first = task;
            count++;
        }
        if (first == null) {
            title.setVisibility(View.GONE);
            due.setVisibility(View.GONE);
        } else {
            title.setText(count > 1 ? getString(R.string.title_more, first.title, count - 1) : first.title);
            title.setVisibility(View.VISIBLE);
            due.setText(first.date == null ? "" : dueLabel(first));
            due.setVisibility(first.date == null ? View.GONE : View.VISIBLE);
        }
        saveButton.setVisibility(text.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    /** "Today · 15:00", "Tomorrow", "Fri", "5 Nov · 09:30". */
    private String dueLabel(TaskParser.Task t) {
        LocalDate today = LocalDate.now();
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, t.date);
        String day;
        if (days == 0) day = getString(R.string.today);
        else if (days == 1) day = getString(R.string.tomorrow);
        else if (days > 1 && days < 7) day = t.date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
        else day = t.date.format(DateTimeFormatter.ofPattern(t.date.getYear() == today.getYear() ? "d MMM" : "d MMM yyyy"));
        return t.time == null ? day : day + "  ·  " + t.time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** Shows the outcome in the card for a moment, then closes. */
    private void done(int message) {
        mic.setEnabled(false);
        saveButton.setVisibility(View.INVISIBLE);
        wave.setMode(VoiceView.Mode.DONE);
        status.setText(message);
        status.postDelayed(this::finish, 1100);
    }

    private void listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (recorder.isRecording()) return;
        mic.setActivated(true);
        wave.setMode(VoiceView.Mode.LISTENING);
        status.setText(""); // the bars say it
        recorder.start(new SpeechRecorder.Listener() {
            @Override
            public void onLevel(float level) {
                wave.setLevel(level);
            }

            @Override
            public void onDone(float[] pcm, int samples, boolean heardSpeech) {
                resetMic();
                if (!heardSpeech || isFinishing()) {
                    status.setText(R.string.heard_nothing);
                    return;
                }
                status.setText("");
                wave.setMode(VoiceView.Mode.THINKING);
                Whistle.transcribe(CaptureActivity.this, pcm, samples, prefs.language(), prefs.keywords(),
                        new Whistle.Callback() {
                            @Override
                            public void onText(String text) {
                                wave.setMode(VoiceView.Mode.IDLE);
                                onHeard(text);
                            }

                            @Override
                            public void onError(String message) {
                                wave.setMode(VoiceView.Mode.IDLE);
                                status.setText(getString(R.string.speech_failed, message));
                            }
                        });
            }

            @Override
            public void onError(String message) {
                resetMic();
                status.setText(getString(R.string.speech_failed, message));
            }
        });
    }

    private void onHeard(String heard) {
        if (isFinishing()) return;
        if (heard.isEmpty()) {
            status.setText(R.string.no_words);
            return;
        }
        status.setText("");
        setText(text.isEmpty() ? heard : text + " " + heard);
        if (prefs.autoSave()) save();
    }

    private void resetMic() {
        mic.setActivated(false);
        if (!isFinishing()) wave.setMode(VoiceView.Mode.IDLE);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] permissions, int[] results) {
        if (req != REQ_MIC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            listen();
        } else {
            status.setText(R.string.mic_denied);
        }
    }

    /** One task per non-blank line, so shared multi-line text becomes several tasks. */
    private String render() {
        LocalDateTime now = LocalDateTime.now();
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            TaskParser.Task t = TaskParser.parse(line, now);
            if (t.title.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(TaskFormatter.format(t, prefs.line(), prefs.due(), prefs.dueTime(), now.toLocalDate()));
        }
        return out.toString();
    }

    private void save() {
        String lines = render();
        if (lines.isEmpty()) return;
        boolean viaObsidian = !Prefs.MODE_FILE.equals(prefs.mode());
        // Obsidian can't open over the lock screen, so park the task until the phone is unlocked.
        if (viaObsidian && keyguard.isKeyguardLocked()) {
            try {
                PendingQueue.add(this, lines);
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.save_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                return;
            }
            done(R.string.queued);
            return;
        }
        // Anything still waiting from the lock screen goes along in the same append.
        String waiting = viaObsidian ? PendingQueue.peek(this) : "";
        try {
            InboxWriter.append(this, prefs, waiting.isEmpty() ? lines : waiting + "\n" + lines);
            if (!waiting.isEmpty()) PendingQueue.clear(this);
        } catch (Exception e) {
            // Keep the text so nothing is lost; the user can fix settings and retry.
            Toast.makeText(this, getString(R.string.save_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        done(R.string.saved);
    }
}
