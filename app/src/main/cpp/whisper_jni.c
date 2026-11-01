#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

/* One model at a time; Java serialises every call on a single thread. */
static struct whisper_context *ctx;

static void quiet(enum ggml_log_level level, const char *text, void *user) {
    (void) level; (void) text; (void) user; /* the app logs nothing */
}

static void throw_state(JNIEnv *env, const char *msg) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), msg);
}

/* Returns null on success, else an error message. Replaces any model already loaded. */
JNIEXPORT jstring JNICALL
Java_io_github_ramiawar_listenhere_Whisper_nativeLoad(JNIEnv *env, jclass cls, jstring path) {
    whisper_log_set(quiet, NULL);
    if (ctx) {
        whisper_free(ctx);
        ctx = NULL;
    }
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    struct whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    ctx = whisper_init_from_file_with_params(p, params);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return ctx ? NULL : (*env)->NewStringUTF(env, "could not load the model file");
}

/* Transcript as UTF-8 bytes (see whistle_jni.c for why not a jstring). */
JNIEXPORT jbyteArray JNICALL
Java_io_github_ramiawar_listenhere_Whisper_nativeTranscribe(JNIEnv *env, jclass cls, jfloatArray pcm, jint samples,
                                                            jstring language, jstring prompt, jint threads) {
    if (!ctx) {
        throw_state(env, "no model loaded");
        return NULL;
    }
    jsize available = (*env)->GetArrayLength(env, pcm);
    if (samples < 0 || samples > available) samples = available;

    const char *lang = language ? (*env)->GetStringUTFChars(env, language, NULL) : NULL;
    const char *hint = prompt ? (*env)->GetStringUTFChars(env, prompt, NULL) : NULL;
    jfloat *audio = (*env)->GetFloatArrayElements(env, pcm, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.translate = false;
    params.no_timestamps = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.language = lang ? lang : "auto";
    params.initial_prompt = hint;  /* names and project words to favour */
    params.suppress_blank = true;

    int rc = whisper_full(ctx, params, audio, samples);

    jbyteArray result = NULL;
    if (rc == 0) {
        size_t cap = 256, len = 0;
        char *text = malloc(cap);
        int n = whisper_full_n_segments(ctx);
        for (int i = 0; text && i < n; i++) {
            const char *seg = whisper_full_get_segment_text(ctx, i);
            size_t sl = strlen(seg);
            if (len + sl + 1 > cap) {
                while (len + sl + 1 > cap) cap *= 2;
                char *grown = realloc(text, cap);
                if (!grown) { free(text); text = NULL; break; }
                text = grown;
            }
            memcpy(text + len, seg, sl);
            len += sl;
        }
        if (text) {
            result = (*env)->NewByteArray(env, (jsize) len);
            if (result) (*env)->SetByteArrayRegion(env, result, 0, (jsize) len, (const jbyte *) text);
            free(text);
        }
    }

    (*env)->ReleaseFloatArrayElements(env, pcm, audio, JNI_ABORT);
    if (lang) (*env)->ReleaseStringUTFChars(env, language, lang);
    if (hint) (*env)->ReleaseStringUTFChars(env, prompt, hint);
    if (rc != 0) throw_state(env, "transcription failed");
    return result;
}
