package io.github.ramiawar.listenhere;

import android.content.Context;

/** The GitHub build bundles Cactus Whistle. */
final class Bundled {
    static final boolean AVAILABLE = true;

    private Bundled() {}

    static SpeechEngine engine(Context c) {
        return new Whistle(c.getApplicationContext());
    }
}
