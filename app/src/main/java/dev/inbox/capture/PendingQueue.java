package dev.inbox.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Tasks captured while the phone is locked, when Obsidian can't be opened. They wait in app
 * storage and go to Obsidian together in one append, signalled by a single notification.
 */
final class PendingQueue {

    private static final String FILE = "pending.md";
    private static final String CHANNEL = "pending";
    private static final int NOTIFICATION_ID = 1;

    private PendingQueue() {}

    static synchronized void add(Context c, String lines) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file(c), true)) {
            out.write((lines + "\n").getBytes(StandardCharsets.UTF_8));
        }
        notify(c, count(c));
    }

    /** Everything waiting, one task per line, or "" when the queue is empty. */
    static synchronized String peek(Context c) {
        File f = file(c);
        if (!f.exists()) return "";
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    static synchronized void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }

    static int count(Context c) {
        String s = peek(c);
        return s.isEmpty() ? 0 : s.split("\n").length;
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    /** Always the same notification id, so 100 queued tasks still means one notification. */
    private static void notify(Context c, int count) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                c.getString(R.string.pending_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent tap = PendingIntent.getActivity(c, 0,
                new Intent(c, FlushActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(c.getResources().getQuantityString(R.plurals.pending_title, count, count))
                .setContentText(c.getString(R.string.pending_text))
                .setContentIntent(tap)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC) // only a count, nothing private
                .build();
        nm.notify(NOTIFICATION_ID, n);
    }
}
