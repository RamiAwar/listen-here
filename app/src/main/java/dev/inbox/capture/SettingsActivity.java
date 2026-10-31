package dev.inbox.capture;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private static final int REQ_PICK = 1;

    private Prefs prefs;
    private EditText vault, note, line, due, dueTime;
    private RadioGroup mode;
    private TextView fileLabel;
    private CompoundButton autoMic, autoSave;
    private Spinner language;
    private EditText keywords;
    private String fileUri;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings);
        prefs = new Prefs(this);

        vault = findViewById(R.id.vault);
        note = findViewById(R.id.note);
        line = findViewById(R.id.line);
        due = findViewById(R.id.due);
        dueTime = findViewById(R.id.due_time);
        mode = findViewById(R.id.mode);
        fileLabel = findViewById(R.id.file_label);
        autoMic = findViewById(R.id.auto_mic);
        autoSave = findViewById(R.id.auto_save);
        language = findViewById(R.id.language);
        keywords = findViewById(R.id.keywords);

        language.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.language_names)));
        language.setSelection(Math.max(0, java.util.Arrays.asList(Prefs.LANGUAGES).indexOf(prefs.languageSetting())));
        keywords.setText(prefs.keywordsSetting());

        vault.setText(prefs.vault());
        note.setText(prefs.note());
        line.setText(prefs.line());
        due.setText(prefs.due());
        dueTime.setText(prefs.dueTime());
        autoMic.setChecked(prefs.autoMic());
        autoSave.setChecked(prefs.autoSave());
        fileUri = prefs.fileUri();
        mode.check(Prefs.MODE_FILE.equals(prefs.mode()) ? R.id.mode_file : R.id.mode_uri);
        mode.setOnCheckedChangeListener((g, id) -> updateVisibility());
        updateVisibility();

        findViewById(R.id.pick).setOnClickListener(v -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*"), REQ_PICK));
        findViewById(R.id.reset).setOnClickListener(v -> {
            line.setText(TaskFormatter.DEFAULT_LINE);
            due.setText(TaskFormatter.DEFAULT_DUE);
            dueTime.setText(TaskFormatter.DEFAULT_DUE_TIME);
        });
        findViewById(R.id.done).setOnClickListener(v -> finish());
    }

    private void updateVisibility() {
        boolean file = mode.getCheckedRadioButtonId() == R.id.mode_file;
        findViewById(R.id.file_group).setVisibility(file ? View.VISIBLE : View.GONE);
        findViewById(R.id.uri_group).setVisibility(file ? View.GONE : View.VISIBLE);
        fileLabel.setText(fileUri == null ? getString(R.string.no_file)
                : Uri.decode(Uri.parse(fileUri).getLastPathSegment()));
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_PICK || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        fileUri = uri.toString();
        updateVisibility();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.sp.edit()
                .putString(Prefs.VAULT, vault.getText().toString().trim())
                .putString(Prefs.NOTE, note.getText().toString().trim())
                .putString(Prefs.MODE, mode.getCheckedRadioButtonId() == R.id.mode_file ? Prefs.MODE_FILE : Prefs.MODE_URI)
                .putString(Prefs.FILE_URI, fileUri)
                .putString(Prefs.LINE, line.getText().toString())
                .putString(Prefs.DUE, due.getText().toString())
                .putString(Prefs.DUE_TIME, dueTime.getText().toString())
                .putBoolean(Prefs.AUTO_MIC, autoMic.isChecked())
                .putBoolean(Prefs.AUTO_SAVE, autoSave.isChecked())
                .putString(Prefs.LANGUAGE, Prefs.LANGUAGES[language.getSelectedItemPosition()])
                .putString(Prefs.KEYWORDS, keywords.getText().toString())
                .apply();
    }
}
