package io.github.ramiawar.listenhere;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String MODE_FILE = "file";
    static final String MODE_URI = "uri";

    static final String VAULT = "vault";
    static final String NOTE = "note";
    static final String MODE = "mode";
    static final String FILE_URI = "file_uri";
    static final String LINE = "line";
    static final String DUE = "due";
    static final String DUE_TIME = "due_time";
    static final String AUTO_SAVE = "auto_save";
    static final String AUTO_MIC = "auto_mic";
    static final String LANGUAGE = "language";
    static final String KEYWORDS = "keywords";
    static final String MODEL = "model";

    /** Whistle's languages; "" lets it detect. */
    static final String[] LANGUAGES = {"en", "nl", "de", "fr", "es", "it", "pl", ""};

    final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    String vault() { return sp.getString(VAULT, ""); }
    String note() { return sp.getString(NOTE, "Inbox"); }
    String mode() { return sp.getString(MODE, MODE_URI); }
    String fileUri() { return sp.getString(FILE_URI, null); }
    String line() { return sp.getString(LINE, TaskFormatter.DEFAULT_LINE); }
    String due() { return sp.getString(DUE, TaskFormatter.DEFAULT_DUE); }
    String dueTime() { return sp.getString(DUE_TIME, TaskFormatter.DEFAULT_DUE_TIME); }
    boolean autoSave() { return sp.getBoolean(AUTO_SAVE, true); }
    boolean autoMic() { return sp.getBoolean(AUTO_MIC, true); }
    String languageSetting() { return sp.getString(LANGUAGE, "en"); }
    String language() { return languageSetting().isEmpty() ? null : languageSetting(); }
    String model() { return sp.getString(MODEL, Bundled.AVAILABLE ? Models.WHISTLE : ""); }
    String keywordsSetting() { return sp.getString(KEYWORDS, ""); }
    String keywords() { return keywordsSetting().trim().isEmpty() ? null : keywordsSetting().trim(); }

    boolean configured() {
        // URI mode works unconfigured: with no vault name Obsidian uses the one that is open.
        return !MODE_FILE.equals(mode()) || fileUri() != null;
    }
}
