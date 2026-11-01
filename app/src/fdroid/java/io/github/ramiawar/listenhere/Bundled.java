package io.github.ramiawar.listenhere;

import android.content.Context;

/** The F-Droid build bundles no model: everything in it is built from source. */
final class Bundled {
    static final boolean AVAILABLE = false;

    private Bundled() {}

    static SpeechEngine engine(Context c) {
        return null;
    }
}
