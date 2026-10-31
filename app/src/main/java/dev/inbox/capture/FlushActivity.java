package dev.inbox.capture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** Opened from the "tasks waiting" notification (after unlock): files the whole queue in one go. */
public class FlushActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String lines = PendingQueue.peek(this);
        if (!lines.isEmpty()) {
            int n = PendingQueue.count(this);
            try {
                InboxWriter.append(this, new Prefs(this), lines);
                PendingQueue.clear(this);
                Toast.makeText(this, getResources().getQuantityString(R.plurals.filed, n, n), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.save_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
}
