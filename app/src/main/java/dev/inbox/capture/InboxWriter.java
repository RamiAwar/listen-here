package dev.inbox.capture;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/** Appends lines to the inbox note, either straight into the file or through Obsidian's URI scheme. */
final class InboxWriter {

    private InboxWriter() {}

    /** Throws on failure so the caller can keep the text on screen instead of losing it. */
    static void append(Context c, Prefs prefs, String lines) throws IOException {
        if (Prefs.MODE_FILE.equals(prefs.mode())) {
            appendToFile(c.getContentResolver(), Uri.parse(prefs.fileUri()), lines);
        } else {
            appendViaObsidian(c, prefs, lines);
        }
    }

    /**
     * Direct write via the Storage Access Framework. Needs no Obsidian round-trip, so it's the
     * fastest path; Obsidian picks the change up the next time it opens.
     */
    private static void appendToFile(ContentResolver cr, Uri uri, String lines) throws IOException {
        boolean needsNewline = false;
        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r")) {
            if (pfd == null) throw new IOException("Cannot open inbox file");
            try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
                FileChannel ch = in.getChannel();
                long size = ch.size();
                if (size > 0) {
                    ch.position(size - 1);
                    needsNewline = in.read() != '\n';
                }
            }
        }
        byte[] data = ((needsNewline ? "\n" : "") + lines + "\n").getBytes(StandardCharsets.UTF_8);
        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "wa")) {
            if (pfd == null) throw new IOException("Cannot open inbox file for writing");
            try (FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
                // Some providers ignore "a"; seek to the end ourselves so nothing gets overwritten.
                out.getChannel().position(out.getChannel().size());
                out.write(data);
            }
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            rewrite(cr, uri, data);
        }
    }

    private static void rewrite(ContentResolver cr, Uri uri, byte[] data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = cr.openInputStream(uri)) {
            if (in != null) {
                byte[] chunk = new byte[8192];
                for (int n; (n = in.read(chunk)) > 0; ) buf.write(chunk, 0, n);
            }
        }
        buf.write(data);
        try (OutputStream out = cr.openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Cannot write inbox file");
            buf.writeTo(out);
        }
    }

    /** obsidian://new?append — goes through Obsidian, so sync plugins (LiveSync etc.) see it immediately. */
    private static void appendViaObsidian(Context c, Prefs prefs, String lines) {
        Uri.Builder b = new Uri.Builder().scheme("obsidian").authority("new");
        if (!prefs.vault().isEmpty()) b.appendQueryParameter("vault", prefs.vault());
        Uri uri = b
                .appendQueryParameter("file", prefs.note())
                // Obsidian inserts the separating newline itself when appending.
                .appendQueryParameter("content", lines)
                .appendQueryParameter("append", "true")
                .appendQueryParameter("silent", "true")
                .build();
        Intent i = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
