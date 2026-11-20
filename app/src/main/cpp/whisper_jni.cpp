// JNI bridge between the app (com.hebtranscribe.ai.LocalWhisper) and whisper.cpp.
// Transcribes 16 kHz mono float PCM entirely on the device.

#include <jni.h>
#include <atomic>
#include <string>
#include <cstring>
#include <android/log.h>
#include "whisper.h"

#define TAG "RimonWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

std::atomic<bool> g_abort{false};

struct ProgressCtx {
    JNIEnv *env;
    jobject listener;
    jmethodID method;
};

void on_progress(struct whisper_context * /*ctx*/, struct whisper_state * /*state*/, int progress, void *user_data) {
    auto *p = static_cast<ProgressCtx *>(user_data);
    if (p == nullptr || p->listener == nullptr || p->method == nullptr) return;
    p->env->CallVoidMethod(p->listener, p->method, (jint) progress);
    if (p->env->ExceptionCheck()) p->env->ExceptionClear();
}

bool should_abort(void * /*user_data*/) {
    return g_abort.load();
}

std::string clean(const char *text) {
    std::string s = text ? text : "";
    for (char &c : s) {
        if (c == '\t' || c == '\n' || c == '\r') c = ' ';
    }
    return s;
}

std::string jstr(JNIEnv *env, jstring s) {
    if (s == nullptr) return "";
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_rimon_smartfamily_smart_LocalWhisper_nativeInit(JNIEnv *env, jobject /*thiz*/, jstring model_path) {
    std::string path = jstr(env, model_path);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    LOGI("init %s -> %p", path.c_str(), (void *) ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_rimon_smartfamily_smart_LocalWhisper_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/, jlong ctx_ptr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    if (ctx != nullptr) whisper_free(ctx);
}

JNIEXPORT void JNICALL
Java_com_rimon_smartfamily_smart_LocalWhisper_nativeAbort(JNIEnv * /*env*/, jobject /*thiz*/, jboolean abort) {
    g_abort.store(abort == JNI_TRUE);
}

JNIEXPORT jstring JNICALL
Java_com_rimon_smartfamily_smart_LocalWhisper_nativeSystemInfo(JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF(whisper_print_system_info());
}

/**
 * Returns UTF-8 bytes:
 *   first line:  "LANG\t<code>"
 *   then one line per segment: "<t0 ms>\t<t1 ms>\t<text>"
 * Returns null on failure or abort.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_rimon_smartfamily_smart_LocalWhisper_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong ctx_ptr, jfloatArray samples, jint threads,
        jstring language, jstring prompt, jobject listener) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    if (ctx == nullptr || samples == nullptr) return nullptr;

    std::string lang = jstr(env, language);
    std::string initial = jstr(env, prompt);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? threads : 4;
    params.translate = false;
    params.no_context = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.single_segment = false;
    params.language = lang.empty() ? "auto" : lang.c_str();
    params.detect_language = false;
    params.initial_prompt = initial.empty() ? nullptr : initial.c_str();
    params.suppress_blank = true;
    params.greedy.best_of = 2;

    jclass cls = listener ? env->GetObjectClass(listener) : nullptr;
    ProgressCtx pctx{env, listener, cls ? env->GetMethodID(cls, "onProgress", "(I)V") : nullptr};
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        pctx.method = nullptr;
    }
    params.progress_callback = on_progress;
    params.progress_callback_user_data = &pctx;
    params.abort_callback = should_abort;
    params.abort_callback_user_data = nullptr;

    jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    if (data == nullptr) return nullptr;

    g_abort.store(false);
    int rc = whisper_full(ctx, params, data, (int) n);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    if (rc != 0 || g_abort.load()) {
        LOGI("whisper_full failed rc=%d abort=%d", rc, (int) g_abort.load());
        return nullptr;
    }

    std::string out = "LANG\t";
    int lang_id = whisper_full_lang_id(ctx);
    const char *code = lang_id >= 0 ? whisper_lang_str(lang_id) : nullptr;
    out += code ? code : "";
    out += "\n";

    const int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; ++i) {
        const int64_t t0 = whisper_full_get_segment_t0(ctx, i) * 10; // centiseconds -> ms
        const int64_t t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        out += std::to_string((long long) t0);
        out += "\t";
        out += std::to_string((long long) t1);
        out += "\t";
        out += clean(whisper_full_get_segment_text(ctx, i));
        out += "\n";
    }

    jbyteArray result = env->NewByteArray((jsize) out.size());
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

} // extern "C"
