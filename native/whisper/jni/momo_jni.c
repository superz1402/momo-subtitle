/*
 * Momo Subtitle — JNI bridge to vendored whisper.cpp v1.7.4.
 *
 * Contract with Kotlin (com.momosubtitle.core.speech.WhisperJni):
 *   initModel(path) -> context pointer or 0
 *   transcribeWav(ptr, path, threads, translate, language) -> JSON string
 *   freeModel(ptr)
 *   systemInfo() -> capability string
 *
 * The Kotlin pipeline always feeds 16 kHz mono 16-bit PCM WAV files
 * (core/media), so the WAV loader here is intentionally strict.
 */
#include <jni.h>
#include <android/log.h>
#include <whisper.h>

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "MomoWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void throw_java(JNIEnv *env, const char *cls, const char *msg) {
    if ((*env)->ExceptionCheck(env)) return;
    jclass c = (*env)->FindClass(env, cls);
    if (c != NULL) (*env)->ThrowNew(env, c, msg);
}

/* ---- growable string buffer for JSON assembly ---- */
typedef struct { char *data; size_t len; size_t cap; } sbuf;

static int sb_init(sbuf *b) {
    b->cap = 8192;
    b->len = 0;
    b->data = (char *)malloc(b->cap);
    if (!b->data) return -1;
    b->data[0] = '\0';
    return 0;
}

static int sb_reserve(sbuf *b, size_t extra) {
    if (b->len + extra + 1 <= b->cap) return 0;
    size_t new_cap = b->cap;
    while (new_cap < b->len + extra + 1) new_cap *= 2;
    char *p = (char *)realloc(b->data, new_cap);
    if (!p) return -1;
    b->data = p;
    b->cap = new_cap;
    return 0;
}

static int sb_append_n(sbuf *b, const char *s, size_t n) {
    if (sb_reserve(b, n) != 0) return -1;
    memcpy(b->data + b->len, s, n);
    b->len += n;
    b->data[b->len] = '\0';
    return 0;
}

static int sb_append(sbuf *b, const char *s) { return sb_append_n(b, s, strlen(s)); }

/* Escapes for JSON; input is UTF-8 already (whisper output). */
static int sb_append_json_escaped(sbuf *b, const char *s) {
    char tmp[8];
    for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
        unsigned char c = *p;
        switch (c) {
            case '"':  if (sb_append(b, "\\\"") != 0) return -1; break;
            case '\\': if (sb_append(b, "\\\\") != 0) return -1; break;
            case '\n': if (sb_append(b, "\\n") != 0) return -1; break;
            case '\r': if (sb_append(b, "\\r") != 0) return -1; break;
            case '\t': if (sb_append(b, "\\t") != 0) return -1; break;
            default:
                if (c < 0x20) {
                    snprintf(tmp, sizeof(tmp), "\\u%04x", (unsigned)c);
                    if (sb_append(b, tmp) != 0) return -1;
                } else {
                    tmp[0] = (char)c;
                    tmp[1] = '\0';
                    if (sb_append_n(b, tmp, 1) != 0) return -1;
                }
        }
    }
    return 0;
}

/* ---- strict 16 kHz mono s16le PCM WAV loader ---- */
/* 0 = ok, -2 = bad file, -3 = wrong format, -4 = oom */
static int load_wav_mono_16k(const char *path, float **out_samples, int *out_n) {
    FILE *f = fopen(path, "rb");
    if (!f) return -2;

    unsigned char hdr[12];
    if (fread(hdr, 1, 12, f) != 12) { fclose(f); return -2; }
    if (memcmp(hdr, "RIFF", 4) != 0 || memcmp(hdr + 8, "WAVE", 4) != 0) { fclose(f); return -2; }

    long data_offset = -1;
    unsigned int data_size = 0;
    int audio_format = 0, channels = 0, sample_rate = 0, bits = 0;

    for (;;) {
        unsigned char chunk[8];
        if (fread(chunk, 1, 8, f) != 8) break;
        unsigned int sz = (unsigned int)chunk[4] |
                          ((unsigned int)chunk[5] << 8) |
                          ((unsigned int)chunk[6] << 16) |
                          ((unsigned int)chunk[7] << 24);
        if (memcmp(chunk, "fmt ", 4) == 0) {
            unsigned char fmt[16];
            if (fread(fmt, 1, 16, f) != 16) { fclose(f); return -2; }
            audio_format = fmt[0] | (fmt[1] << 8);
            channels     = fmt[2] | (fmt[3] << 8);
            sample_rate  = fmt[4] | (fmt[5] << 8) | (fmt[6] << 16) | ((int)fmt[7] << 24);
            bits         = fmt[14] | (fmt[15] << 8);
            if (sz > 16 && fseek(f, (long)(sz - 16), SEEK_CUR) != 0) { fclose(f); return -2; }
        } else if (memcmp(chunk, "data", 4) == 0) {
            data_offset = ftell(f);
            data_size = sz;
            break;
        } else {
            if (fseek(f, (long)sz + (sz & 1), SEEK_CUR) != 0) break;
        }
    }

    if (data_offset < 0) { fclose(f); return -2; }
    if (audio_format != 1 || bits != 16 || channels != 1 || sample_rate != 16000) {
        fclose(f);
        return -3;
    }

    int n = (int)(data_size / 2);
    if (n <= 0) { fclose(f); return -2; }

    short *raw = (short *)malloc((size_t)data_size);
    float *samples = (float *)malloc(sizeof(float) * (size_t)n);
    if (!raw || !samples) {
        free(raw);
        free(samples);
        fclose(f);
        return -4;
    }
    size_t got = fread(raw, 1, data_size, f);
    fclose(f);
    if (got < (size_t)data_size) {
        n = (int)(got / 2);
        if (n <= 0) { free(raw); free(samples); return -2; }
    }
    for (int i = 0; i < n; i++) samples[i] = (float)(raw[i] / 32768.0);
    free(raw);

    *out_samples = samples;
    *out_n = n;
    return 0;
}

/* ---- JNI entry points ---- */

JNIEXPORT jlong JNICALL
Java_com_momosubtitle_core_speech_WhisperJni_initModel(JNIEnv *env, jobject thiz, jstring model_path) {
    (void)thiz;
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    if (!path) return 0;

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    LOGI("whisper_init_from_file_with_params(%s) -> %s", path, ctx ? "ok" : "FAILED");
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    if (!ctx) {
        throw_java(env, "java/lang/IllegalStateException", "Failed to load Whisper model file");
        return 0;
    }
    return (jlong)(intptr_t)ctx;
}

JNIEXPORT void JNICALL
Java_com_momosubtitle_core_speech_WhisperJni_freeModel(JNIEnv *env, jobject thiz, jlong ptr) {
    (void)env;
    (void)thiz;
    if (ptr != 0) whisper_free((struct whisper_context *)(intptr_t)ptr);
}

JNIEXPORT jstring JNICALL
Java_com_momosubtitle_core_speech_WhisperJni_transcribeWav(
        JNIEnv *env, jobject thiz, jlong ptr, jstring wav_path,
        jint threads, jboolean translate, jstring language) {
    (void)thiz;
    struct whisper_context *ctx = (struct whisper_context *)(intptr_t)ptr;
    if (!ctx) {
        throw_java(env, "java/lang/IllegalStateException", "Whisper model not loaded");
        return NULL;
    }

    const char *path = (*env)->GetStringUTFChars(env, wav_path, NULL);
    const char *lang = language ? (*env)->GetStringUTFChars(env, language, NULL) : NULL;

    float *samples = NULL;
    int n_samples = 0;
    int rc = load_wav_mono_16k(path, &samples, &n_samples);
    (*env)->ReleaseStringUTFChars(env, wav_path, path);

    if (rc == -3) {
        if (lang) (*env)->ReleaseStringUTFChars(env, language, lang);
        throw_java(env, "java/lang/IllegalArgumentException",
                   "WAV must be 16 kHz mono 16-bit PCM");
        return NULL;
    }
    if (rc != 0 || n_samples <= 0) {
        if (lang) (*env)->ReleaseStringUTFChars(env, language, lang);
        free(samples);
        throw_java(env, "java/io/IOException", "Failed to read WAV audio data");
        return NULL;
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? (int)threads : 2;
    params.translate = (translate == JNI_TRUE);
    params.language = (lang && lang[0] != '\0') ? lang : "auto";
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.no_context = true;      /* chunks are independent windows */
    params.single_segment = false;

    int run_rc = whisper_full(ctx, params, samples, n_samples);
    free(samples);
    if (run_rc != 0) {
        if (lang) (*env)->ReleaseStringUTFChars(env, language, lang);
        throw_java(env, "java/lang/RuntimeException", "whisper_full failed");
        return NULL;
    }

    int lang_id = whisper_full_lang_id(ctx);
    const char *lang_str = whisper_lang_str(lang_id);

    int n_seg = whisper_full_n_segments(ctx);
    long duration_ms = (long)((double)n_samples / 16000.0 * 1000.0);

    sbuf b;
    if (sb_init(&b) != 0) {
        if (lang) (*env)->ReleaseStringUTFChars(env, language, lang);
        throw_java(env, "java/lang/OutOfMemoryError", "JSON buffer");
        return NULL;
    }

    char tmp[160];
    sb_append(&b, "{\"language\":\"");
    sb_append(&b, lang_str ? lang_str : "unknown");
    snprintf(tmp, sizeof(tmp), "\",\"durationMs\":%ld,\"segments\":[", duration_ms);
    sb_append(&b, tmp);

    for (int i = 0; i < n_seg; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        int64_t t0 = whisper_full_get_segment_t0(ctx, i); /* centiseconds */
        int64_t t1 = whisper_full_get_segment_t1(ctx, i);
        snprintf(tmp, sizeof(tmp), "%s{\"startMs\":%lld,\"endMs\":%lld,\"text\":\"",
                 i > 0 ? "," : "",
                 (long long)(t0 * 10), (long long)(t1 * 10));
        sb_append(&b, tmp);
        sb_append_json_escaped(&b, text);
        sb_append(&b, "\"}");
    }
    sb_append(&b, "]}");

    if (lang) (*env)->ReleaseStringUTFChars(env, language, lang);

    jstring result = (*env)->NewStringUTF(env, b.data);
    free(b.data);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_momosubtitle_core_speech_WhisperJni_systemInfo(JNIEnv *env, jobject thiz) {
    (void)thiz;
    const char *info = whisper_print_system_info();
    return (*env)->NewStringUTF(env, info ? info : "");
}
