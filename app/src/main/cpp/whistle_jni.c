#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "needle.h"

#define OUT_CAP (64 * 1024)

static jstring error(JNIEnv *env) {
    const char *e = needle_last_error();
    return (*env)->NewStringUTF(env, e ? e : "unknown error");
}

/* Returns null on success, else the engine's error. The buffer must stay alive for the process. */
JNIEXPORT jstring JNICALL
Java_io_github_ramiawar_listenhere_Whistle_nativeLoad(JNIEnv *env, jclass cls, jobject buf) {
    const unsigned char *p = (*env)->GetDirectBufferAddress(env, buf);
    jlong n = (*env)->GetDirectBufferCapacity(env, buf);
    if (!p || n <= 0) return (*env)->NewStringUTF(env, "model buffer is not direct");
    return needle_load(p, (unsigned long long) n) < 0 ? error(env) : NULL;
}

/* Returns the engine's JSON ({"text":..,"language":..}) as UTF-8 bytes, or throws IllegalStateException.
   Bytes, not a jstring: NewStringUTF wants modified UTF-8 and aborts on 4-byte sequences (emoji). */
JNIEXPORT jbyteArray JNICALL
Java_io_github_ramiawar_listenhere_Whistle_nativeTranscribe(JNIEnv *env, jclass cls, jfloatArray pcm,
                                                jint samples, jstring language, jstring keywords) {
    const char *lang = language ? (*env)->GetStringUTFChars(env, language, NULL) : NULL;
    const char *kw = keywords ? (*env)->GetStringUTFChars(env, keywords, NULL) : NULL;
    jsize available = (*env)->GetArrayLength(env, pcm);
    if (samples < 0 || samples > available) samples = available; /* never read past the array */
    jfloat *audio = (*env)->GetFloatArrayElements(env, pcm, NULL);
    char *out = malloc(OUT_CAP);

    int rc = out ? needle_transcribe(audio, samples, lang, kw, 0, out, OUT_CAP) : -1;
    jbyteArray result = NULL;
    if (rc >= 0) {
        out[OUT_CAP - 1] = '\0';
        jsize len = (jsize) strlen(out);
        result = (*env)->NewByteArray(env, len);
        if (result) (*env)->SetByteArrayRegion(env, result, 0, len, (const jbyte *) out);
    }
    if (rc < 0) {
        jclass ex = (*env)->FindClass(env, "java/lang/IllegalStateException");
        const char *e = needle_last_error();
        (*env)->ThrowNew(env, ex, e ? e : "transcription failed");
    }

    free(out);
    (*env)->ReleaseFloatArrayElements(env, pcm, audio, JNI_ABORT);
    if (lang) (*env)->ReleaseStringUTFChars(env, language, lang);
    if (kw) (*env)->ReleaseStringUTFChars(env, keywords, kw);
    return result;
}
