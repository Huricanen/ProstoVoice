#include <jni.h>
#include <android/log.h>
#include <string>
#include <whisper.h>

namespace {

jstring ToString(JNIEnv* env, const char* value) {
    return env->NewStringUTF(value);
}

jstring ToString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string EscapeJson(const char* value) {
    std::string result;
    for (const char* p = value ? value : ""; *p; ++p) {
        if (*p == '\\' || *p == '"') result += '\\';
        if (*p == '\n') result += "\\n";
        else if (*p == '\r') result += "\\r";
        else result += *p;
    }
    return result;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeBackendStatus(
        JNIEnv* env,
        jobject /* self */) {
    return ToString(env,
                    "{\"status\":\"ready\",\"offline\":true,"
                    "\"whisper\":\"whisper.cpp\","
                    "\"diarization\":\"not_configured\"}");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeTranscribePcm(
        JNIEnv* env,
        jobject /* self */,
        jfloatArray pcm,
        jint sample_rate,
        jstring model_path) {
    if (pcm == nullptr || env->GetArrayLength(pcm) == 0) {
        return ToString(env, "{\"status\":\"invalid_input\",\"engine\":\"whisper.cpp\"}");
    }
    if (sample_rate <= 0 || model_path == nullptr) {
        return ToString(env, "{\"status\":\"invalid_config\",\"engine\":\"whisper.cpp\"}");
    }
    const jsize sample_count = env->GetArrayLength(pcm);
    __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceWhisper", "Starting inference: %d samples, %d Hz", sample_count, sample_rate);
    jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);
    const char* model = env->GetStringUTFChars(model_path, nullptr);
    auto context_params = whisper_context_default_params();
    whisper_context* context = whisper_init_from_file_with_params(model, context_params);
    env->ReleaseStringUTFChars(model_path, model);
    if (context == nullptr) {
        env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
        return ToString(env, "{\"status\":\"model_load_failed\",\"engine\":\"whisper.cpp\"}");
    }
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.no_context = true;
    params.language = "auto";
    params.n_threads = 4;
    const int result = whisper_full(context, params, samples, static_cast<int>(sample_count));
    __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceWhisper", "whisper_full returned %d", result);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (result != 0) {
        whisper_free(context);
        return ToString(env, "{\"status\":\"inference_failed\",\"engine\":\"whisper.cpp\"}");
    }
    std::string json = "{\"status\":\"ok\",\"engine\":\"whisper.cpp\",\"segments\":[";
    const int count = whisper_full_n_segments(context);
    for (int i = 0; i < count; ++i) {
        if (i > 0) json += ',';
        json += "{\"start_ms\":" + std::to_string(whisper_full_get_segment_t0(context, i) * 10) +
                ",\"end_ms\":" + std::to_string(whisper_full_get_segment_t1(context, i) * 10) +
                ",\"text\":\"" + EscapeJson(whisper_full_get_segment_text(context, i)) + "\"}";
    }
    json += "]}";
    whisper_free(context);
    return ToString(env, json);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeDiarizePcm(
        JNIEnv* env,
        jobject /* self */,
        jfloatArray pcm,
        jint sample_rate,
        jstring segmentation_model_path,
        jstring embedding_model_path) {
    if (pcm == nullptr || env->GetArrayLength(pcm) == 0) {
        return ToString(env, "{\"status\":\"invalid_input\",\"engine\":\"sherpa-onnx\"}");
    }
    if (sample_rate <= 0 || segmentation_model_path == nullptr || embedding_model_path == nullptr) {
        return ToString(env, "{\"status\":\"invalid_config\",\"engine\":\"sherpa-onnx\"}");
    }
    // Model compatibility and runtime wiring are handled in the diarization stage.
    return ToString(env, "{\"status\":\"not_configured\",\"engine\":\"sherpa-onnx\",\"offline\":true}");
}
