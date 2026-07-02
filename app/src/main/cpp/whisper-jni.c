// Minimal JNI bridge to whisper.cpp for on-device speech recognition.
// Exposes three calls used by WhisperLib.kt: init a model, transcribe a float PCM buffer to text,
// and free the model. Kept deliberately small; all audio capture / VAD lives in Kotlin.

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "whisper.h"

#define TAG "whisper-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_englishteacher_britspeak_speech_WhisperLib_initContext(
        JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    if (ctx == NULL) {
        LOGE("whisper_init_from_file failed");
    }
    return (jlong) (intptr_t) ctx;
}

JNIEXPORT void JNICALL
Java_com_englishteacher_britspeak_speech_WhisperLib_freeContext(
        JNIEnv *env, jobject thiz, jlong ptr) {
    struct whisper_context *ctx = (struct whisper_context *) (intptr_t) ptr;
    if (ctx != NULL) {
        whisper_free(ctx);
    }
}

JNIEXPORT jstring JNICALL
Java_com_englishteacher_britspeak_speech_WhisperLib_transcribe(
        JNIEnv *env, jobject thiz, jlong ptr, jint num_threads, jfloatArray audio) {
    struct whisper_context *ctx = (struct whisper_context *) (intptr_t) ptr;
    if (ctx == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    jsize n_samples = (*env)->GetArrayLength(env, audio);
    jfloat *samples = (*env)->GetFloatArrayElements(env, audio, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = num_threads;
    params.no_context = true;
    params.single_segment = false;
    params.suppress_blank = true;

    // Big latency win for short utterances: whisper's encoder always processes a full 30 s
    // window (1500 positions, 50/s). Shrinking audio_ctx to just cover the actual audio (plus a
    // safety margin) makes encoding ~O(ctx^2) cheaper — several times faster for a few-second
    // conversational turn, with negligible accuracy impact at this margin.
    {
        int audio_ctx = (int) ((double) n_samples / 16000.0 * 50.0) + 64;
        if (audio_ctx < 192)  audio_ctx = 192;
        if (audio_ctx > 1500) audio_ctx = 1500;
        params.audio_ctx = audio_ctx;
    }

    jstring result;
    int rc = whisper_full(ctx, params, (const float *) samples, (int) n_samples);
    (*env)->ReleaseFloatArrayElements(env, audio, samples, JNI_ABORT);

    if (rc != 0) {
        LOGE("whisper_full failed: %d", rc);
        return (*env)->NewStringUTF(env, "");
    }

    // Concatenate all segment texts into one buffer.
    int n_segments = whisper_full_n_segments(ctx);
    size_t total = 1;
    for (int i = 0; i < n_segments; i++) {
        const char *seg = whisper_full_get_segment_text(ctx, i);
        if (seg != NULL) total += strlen(seg);
    }
    char *out = (char *) malloc(total);
    if (out == NULL) {
        return (*env)->NewStringUTF(env, "");
    }
    out[0] = '\0';
    for (int i = 0; i < n_segments; i++) {
        const char *seg = whisper_full_get_segment_text(ctx, i);
        if (seg != NULL) strcat(out, seg);
    }
    result = (*env)->NewStringUTF(env, out);
    free(out);
    return result;
}
