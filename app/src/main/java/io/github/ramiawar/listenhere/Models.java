package io.github.ramiawar.listenhere;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Speech models you can pick. Whistle ships in the GitHub build; Whisper models are fetched in your
 * browser and imported, so the app itself never needs the internet. An import is accepted only if
 * its SHA-256 matches a file in this pinned catalog.
 */
final class Models {

    static final String WHISTLE = "whistle";

    /** whisper.cpp's model repo, pinned to one revision so the files (and hashes) can't change. */
    private static final String REPO =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/";

    static final class Model {
        final String id;
        final String label;
        final String file;
        final String sha256;
        final long bytes;
        final boolean englishOnly;

        Model(String id, String label, String file, String sha256, long bytes, boolean englishOnly) {
            this.id = id;
            this.label = label;
            this.file = file;
            this.sha256 = sha256;
            this.bytes = bytes;
            this.englishOnly = englishOnly;
        }

        Uri downloadUri() {
            return Uri.parse(REPO + file + "?download=true");
        }

        String size() {
            return Math.round(bytes / 1_000_000.0) + " MB";
        }
    }

    static final Model[] WHISPER = {
            new Model("tiny.en", "Whisper tiny · English", "ggml-tiny.en-q5_1.bin",
                    "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b", 32_166_155L, true),
            new Model("base.en", "Whisper base · English", "ggml-base.en-q5_1.bin",
                    "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f", 59_721_011L, true),
            new Model("small.en", "Whisper small · English", "ggml-small.en-q5_1.bin",
                    "bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30", 190_098_681L, true),
            new Model("base", "Whisper base · multilingual", "ggml-base-q5_1.bin",
                    "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", 59_707_625L, false),
            new Model("small", "Whisper small · multilingual", "ggml-small-q5_1.bin",
                    "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", 190_085_487L, false),
    };

    private static final long MAX_IMPORT = 250_000_000L;

    private Models() {}

    static Model byId(String id) {
        for (Model m : WHISPER) if (m.id.equals(id)) return m;
        return null;
    }

    static File file(Context c, Model m) {
        return new File(dir(c), m.file);
    }

    static boolean installed(Context c, Model m) {
        return file(c, m).length() == m.bytes;
    }

    static void remove(Context c, Model m) {
        //noinspection ResultOfMethodCallIgnored
        file(c, m).delete();
    }

    /** The model to use: the chosen one if usable, else Whistle, else any installed Whisper model. */
    static String selected(Context c, Prefs prefs) {
        String id = prefs.model();
        if (WHISTLE.equals(id) && Bundled.AVAILABLE) return id;
        Model m = byId(id);
        if (m != null && installed(c, m)) return id;
        if (Bundled.AVAILABLE) return WHISTLE;
        for (Model w : WHISPER) if (installed(c, w)) return w.id;
        return null;
    }

    static SpeechEngine engine(Context c, Prefs prefs) {
        String id = selected(c, prefs);
        if (id == null) return null;
        if (WHISTLE.equals(id)) return Bundled.engine(c);
        Model m = byId(id);
        return new Whisper(m, file(c, m));
    }

    /**
     * Copies a picked file into app storage, hashing as it goes, and keeps it only if the hash is in
     * the catalog. @return the model it turned out to be.
     */
    static Model importFrom(Context c, Uri uri) throws IOException {
        File tmp = new File(dir(c), "import.tmp");
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IOException("can't open the file");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) {
                total += n;
                if (total > MAX_IMPORT) throw new IOException("too large to be a listed model");
                sha.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        String hex = hex(sha.digest());
        for (Model m : WHISPER) {
            if (m.sha256.equals(hex) && m.bytes == total) {
                if (!tmp.renameTo(file(c, m))) throw new IOException("couldn't store the model");
                return m;
            }
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        throw new IOException("not one of the listed model files");
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "models");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
