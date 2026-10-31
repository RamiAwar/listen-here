package dev.inbox.capture;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
    private String titleText; // what the result screen types out
    private String dueText;
    private int revealGen; // bumps to cancel a reveal still typing
    private VoiceView wave;
    private TextView status;
    private TextView title;
    private TextView due;
    private View card;
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
        card = findViewById(R.id.card);
        keyguard = getSystemService(KeyguardManager.class);
        // No buttons: tap the card to stop early or listen again, long-press for settings.
        card.setOnClickListener(v -> {
            if (recorder.isRecording()) recorder.stop();
            else listen();
        });
        card.setOnLongClickListener(v -> {
            if (keyguard.isKeyguardLocked()) return false; // over the lock screen: capture only
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
        saveButton = findViewById(R.id.save);
        saveButton.setOnClickListener(v -> save());

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
            hideWave(() -> reveal(true));
        } else if (prefs.autoMic()) {
            listen();
        }
    }

    /** Parses what was said into the result screen's title and date; reveal() shows them. */
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
        titleText = first == null ? null
                : count > 1 ? getString(R.string.title_more, first.title, count - 1) : first.title;
        dueText = first == null || first.date == null ? null : dueLabel(first);
    }

    /** Types the title out quickly, then pops in the date (and Save when asked). Layout is final from the start. */
    private void reveal(boolean withSave) {
        if (titleText == null) return;
        int gen = ++revealGen;
        String full = titleText;
        int n = full.length();
        long step = Math.max(6, Math.min(20, 350 / Math.max(1, n)));
        title.setText(typed(full, 0));
        title.setVisibility(View.VISIBLE);
        for (int i = 1; i <= n; i++) {
            int k = i;
            title.postDelayed(() -> {
                if (gen == revealGen) title.setText(typed(full, k));
            }, i * step);
        }
        long after = n * step + 60;
        if (dueText != null) {
            due.setText(dueText);
            popIn(due, after);
        }
        if (withSave) popIn(saveButton, after + 80);
    }

    /** The whole string is laid out, the untyped part transparent, so centring never shifts. */
    private static CharSequence typed(String s, int shown) {
        SpannableString out = new SpannableString(s);
        if (shown < s.length()) {
            out.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), shown, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    private static void popIn(View v, long delay) {
        v.setAlpha(0);
        v.setScaleX(0.9f);
        v.setScaleY(0.9f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(delay).setDuration(180);
    }

    private void hideResult() {
        revealGen++;
        title.setVisibility(View.GONE);
        due.setVisibility(View.GONE);
        saveButton.setVisibility(View.GONE);
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
    /** End screen: just the result, long enough to read. Tap to close sooner. */
    private void done() {
        card.setOnClickListener(v -> finish());
        card.setOnLongClickListener(null);
        // A Save that was showing keeps its space so the text doesn't move; otherwise it takes none.
        if (saveButton.getVisibility() == View.VISIBLE) saveButton.setVisibility(View.INVISIBLE);
        setStatus(null);
        status.postDelayed(this::finish, 4000);
    }

    /** The dots fade out; a beat later, {@code then} runs. */
    private void hideWave(Runnable then) {
        wave.setMode(VoiceView.Mode.DONE);
        wave.animate().alpha(0).setDuration(200).withEndAction(() -> {
            wave.setVisibility(View.INVISIBLE);
            wave.postDelayed(then, 50);
        });
    }

    private void listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (recorder.isRecording()) return;
        hideResult();
        wave.animate().cancel();
        wave.setVisibility(View.VISIBLE);
        wave.setAlpha(1);
        wave.setMode(VoiceView.Mode.IDLE); // bars come up once the mic is actually live
        setStatus(null); // the bars say it
        recorder.start(new SpeechRecorder.Listener() {
            @Override
            public void onReady() {
                wave.setMode(VoiceView.Mode.LISTENING);
            }

            @Override
            public void onLevel(float level) {
                wave.setLevel(level);
            }

            @Override
            public void onDone(float[] pcm, int samples, boolean heardSpeech) {
                showIdle();
                if (!heardSpeech || isFinishing()) {
                    setStatus(getString(R.string.heard_nothing));
                    return;
                }
                setStatus(null);
                wave.setMode(VoiceView.Mode.THINKING);
                Whistle.transcribe(CaptureActivity.this, pcm, samples, prefs.language(), prefs.keywords(),
                        new Whistle.Callback() {
                            @Override
                            public void onText(String text) {
                                onHeard(text);
                            }

                            @Override
                            public void onError(String message) {
                                wave.setMode(VoiceView.Mode.IDLE);
                                setStatus(getString(R.string.speech_failed, message));
                            }
                        });
            }

            @Override
            public void onError(String message) {
                showIdle();
                setStatus(getString(R.string.speech_failed, message));
            }
        });
    }

    private void onHeard(String heard) {
        if (isFinishing()) return;
        if (heard.isEmpty()) {
            wave.setMode(VoiceView.Mode.IDLE); // dots slide back out into the row
            setStatus(getString(R.string.no_words));
            return;
        }
        setStatus(null);
        setText(text.isEmpty() ? heard : text + " " + heard);
        boolean auto = prefs.autoSave();
        hideWave(() -> reveal(!auto));
        if (auto) save();
    }

    /** Hidden when empty, so the card's contents stay truly centred. */
    private void setStatus(String text) {
        status.setText(text);
        status.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showIdle() {
        if (!isFinishing()) wave.setMode(VoiceView.Mode.IDLE);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] permissions, int[] results) {
        if (req != REQ_MIC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            listen();
        } else {
            setStatus(getString(R.string.mic_denied));
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
            done();
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
        done();
    }
}
