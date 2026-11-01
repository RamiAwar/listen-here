package io.github.ramiawar.listenhere;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private static final int REQ_PICK = 1;
    private static final int REQ_MODEL = 2;

    private Prefs prefs;
    private EditText vault, note, line, due, dueTime;
    private RadioGroup mode;
    private TextView fileLabel;
    private CompoundButton autoMic, autoSave;
    private Spinner language;
    private EditText keywords;
    private RadioGroup models;
    private TextView modelStatus;
    private String chosenModel; // only ever an installed model
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

        models = findViewById(R.id.models);
        modelStatus = findViewById(R.id.model_status);
        showModels(Models.selected(this, prefs));
        findViewById(R.id.import_model).setOnClickListener(v -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*"), REQ_MODEL));

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

    /** One radio per model; ones not yet installed download (in the browser) instead of selecting. */
    private void showModels(String selected) {
        chosenModel = selected;
        models.removeAllViews();
        if (Bundled.AVAILABLE) addModel(Models.WHISTLE, getString(R.string.model_whistle), true, null);
        for (Models.Model m : Models.WHISPER) {
            boolean have = Models.installed(this, m);
            addModel(m.id, getString(have ? R.string.model_ready : R.string.model_get, m.label, m.size()), have, m);
        }
        for (int i = 0; i < models.getChildCount(); i++) {
            View v = models.getChildAt(i);
            if (v.getTag().equals(selected)) models.check(v.getId());
        }
    }

    private void addModel(String id, String label, boolean installed, Models.Model whisper) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setTag(id);
        rb.setText(label);
        if (!installed) {
            rb.setOnClickListener(v -> {
                showModels(chosenModel); // undo the tick: there's nothing to select yet
                startActivity(new Intent(Intent.ACTION_VIEW, whisper.downloadUri()));
                modelStatus.setText(R.string.model_downloading);
            });
        } else {
            rb.setOnClickListener(v -> chosenModel = id);
        }
        if (installed && whisper != null) {
            rb.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.model_remove, whisper.label))
                        .setPositiveButton(R.string.remove, (d, w) -> {
                            Models.remove(this, whisper);
                            prefs.sp.edit().putString(Prefs.MODEL, "").apply();
                            showModels(Models.selected(this, prefs));
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }
        models.addView(rb);
    }

    private void importModel(Uri uri) {
        modelStatus.setText(R.string.model_checking);
        new Thread(() -> {
            String message;
            String select = null;
            try {
                Models.Model m = Models.importFrom(this, uri);
                message = getString(R.string.model_imported, m.label);
                select = m.id;
            } catch (Exception e) {
                message = getString(R.string.model_import_failed, e.getMessage());
            }
            String msg = message, id = select;
            runOnUiThread(() -> {
                modelStatus.setText(msg);
                showModels(id != null ? id : chosenModel);
            });
        }, "import").start();
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
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_MODEL) {
            importModel(data.getData());
            return;
        }
        if (req != REQ_PICK) return;
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
                .putString(Prefs.MODEL, chosenModel)
                .apply();
    }
}
