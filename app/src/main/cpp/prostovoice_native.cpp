#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <thread>
#include <string>
#include <mutex>
#include <exception>
#include <whisper.h>
#include <c-api.h>

namespace {

std::mutex g_whisper_mutex;
std::mutex g_diarization_mutex;
whisper_context* g_whisper_context = nullptr;
std::string g_whisper_model_path;

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
    const unsigned int cores = std::thread::hardware_concurrency();
    const unsigned int selected = cores > 2 ? std::min(8u, cores - 1u) : 2u;
    return ToString(env, "{\"status\":\"ready\",\"offline\":true,\"whisper\":\"whisper.cpp\",\"diarization\":\"not_configured\",\"cpu_cores\":" + std::to_string(cores) + ",\"whisper_threads\":" + std::to_string(selected) + "}");
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
    std::lock_guard<std::mutex> lock(g_whisper_mutex);
    if (g_whisper_context == nullptr || g_whisper_model_path != model) {
        if (g_whisper_context != nullptr) whisper_free(g_whisper_context);
        auto context_params = whisper_context_default_params();
        g_whisper_context = whisper_init_from_file_with_params(model, context_params);
        g_whisper_model_path = model;
    }
    env->ReleaseStringUTFChars(model_path, model);
    whisper_context* context = g_whisper_context;
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
    const unsigned int cores = std::thread::hardware_concurrency();
    params.n_threads = static_cast<int>(cores > 2 ? std::min(8u, cores - 1u) : 2u);
    __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceWhisper", "Using %d threads", params.n_threads);
    const int result = whisper_full(context, params, samples, static_cast<int>(sample_count));
    __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceWhisper", "whisper_full returned %d", result);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (result != 0) {
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
    return ToString(env, json);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeDiarizePcm(
        JNIEnv* env,
        jobject /* self */,
        jfloatArray pcm,
        jint sample_rate,
        jstring segmentation_model_path,
        jstring embedding_model_path,
        jint num_clusters,
        jfloat clustering_threshold,
        jfloat window_shift_ratio,
        jfloat min_duration_on,
        jfloat min_duration_off) {
    if (pcm == nullptr || env->GetArrayLength(pcm) == 0) {
        return ToString(env, "{\"status\":\"invalid_input\",\"engine\":\"sherpa-onnx\"}");
    }
    if (sample_rate <= 0 || segmentation_model_path == nullptr || embedding_model_path == nullptr) {
        return ToString(env, "{\"status\":\"invalid_config\",\"engine\":\"sherpa-onnx\"}");
    }
    const jsize sample_count = env->GetArrayLength(pcm);
    if (sample_count <= 0 || sample_rate != 16000) {
        __android_log_print(ANDROID_LOG_ERROR, "ProstoVoiceDiarization",
                            "Invalid audio input: samples=%d sample_rate=%d",
                            sample_count, sample_rate);
        return ToString(env, "{\"status\":\"invalid_input\",\"engine\":\"sherpa-onnx\"}");
    }
    std::lock_guard<std::mutex> diarization_lock(g_diarization_mutex);
    jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);
    const char* segmentation_model = env->GetStringUTFChars(segmentation_model_path, nullptr);
    const char* embedding_model = env->GetStringUTFChars(embedding_model_path, nullptr);
    SherpaOnnxOfflineSpeakerDiarizationConfig config{};
    config.segmentation.pyannote.model = segmentation_model;
    config.segmentation.pyannote.window_shift_ratio = window_shift_ratio;
    config.segmentation.num_threads = 2;
    config.segmentation.provider = "cpu";
    config.embedding.model = embedding_model;
    config.embedding.num_threads = 2;
    config.embedding.provider = "cpu";
    config.clustering.num_clusters = num_clusters;
    config.clustering.threshold = clustering_threshold;
    config.clustering.compute_confidence = 1;
    config.min_duration_on = min_duration_on;
    config.min_duration_off = min_duration_off;
    __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceDiarization",
                        "Creating diarizer: samples=%d duration_ms=%d clusters=%d threshold=%.3f shift=%.3f on=%.3f off=%.3f segmentation=%s embedding=%s",
                        sample_count, static_cast<int>(sample_count * 1000LL / sample_rate),
                        num_clusters, clustering_threshold, window_shift_ratio, min_duration_on, min_duration_off,
                        segmentation_model, embedding_model);
    const SherpaOnnxOfflineSpeakerDiarization* diarizer = nullptr;
    try {
        diarizer = SherpaOnnxCreateOfflineSpeakerDiarization(&config);
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, "ProstoVoiceDiarization",
                            "Diarizer creation exception: %s", error.what());
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, "ProstoVoiceDiarization",
                            "Diarizer creation unknown exception");
    }
    env->ReleaseStringUTFChars(segmentation_model_path, segmentation_model);
    env->ReleaseStringUTFChars(embedding_model_path, embedding_model);
    if (diarizer == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "ProstoVoiceDiarization",
                            "Diarizer creation failed");
        env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
        return ToString(env, "{\"status\":\"model_load_failed\",\"engine\":\"sherpa-onnx\"}");
    }
    __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceDiarization",
                        "Starting diarization inference");
    const SherpaOnnxOfflineSpeakerDiarizationResult* result = nullptr;
    try {
        result = SherpaOnnxOfflineSpeakerDiarizationProcess(diarizer, samples, sample_count);
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, "ProstoVoiceDiarization",
                            "Diarization inference exception: %s", error.what());
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, "ProstoVoiceDiarization",
                            "Diarization inference unknown exception");
    }
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (result == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "ProstoVoiceDiarization",
                            "Diarization inference returned null");
        SherpaOnnxDestroyOfflineSpeakerDiarization(diarizer);
        return ToString(env, "{\"status\":\"inference_failed\",\"engine\":\"sherpa-onnx\"}");
    }
    const int count = SherpaOnnxOfflineSpeakerDiarizationResultGetNumSegments(result);
    const int speakers = SherpaOnnxOfflineSpeakerDiarizationResultGetNumSpeakers(result);
    __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceDiarization",
                        "Diarization returned %d speakers and %d segments",
                        speakers, count);
    const auto* segments = SherpaOnnxOfflineSpeakerDiarizationResultSortByStartTime(result);
    std::string json = "{\"status\":\"ok\",\"engine\":\"sherpa-onnx\",\"segments\":[";
    for (int i = 0; i < count; ++i) {
        __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceDiarization",
                            "Raw segment %d: start_ms=%d end_ms=%d speaker=%d",
                            i, static_cast<int>(segments[i].start * 1000),
                            static_cast<int>(segments[i].end * 1000), segments[i].speaker + 1);
        if (i > 0) json += ',';
        json += "{\"start_ms\":" + std::to_string(static_cast<int>(segments[i].start * 1000)) +
                ",\"end_ms\":" + std::to_string(static_cast<int>(segments[i].end * 1000)) +
                ",\"speaker\":\"speaker_" + std::to_string(segments[i].speaker + 1) + "\"}";
    }
    json += "]}";
    SherpaOnnxOfflineSpeakerDiarizationDestroySegment(segments);
    SherpaOnnxOfflineSpeakerDiarizationDestroyResult(result);
    SherpaOnnxDestroyOfflineSpeakerDiarization(diarizer);
    __android_log_print(ANDROID_LOG_INFO, "ProstoVoiceDiarization",
                        "Diarization completed successfully");
    return ToString(env, json);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeExtractWindowEmbeddings(
        JNIEnv* env, jobject /* self */, jfloatArray pcm, jint sample_rate,
        jstring embedding_model_path, jint window_ms, jint step_ms) {
    if (pcm == nullptr || env->GetArrayLength(pcm) == 0 || sample_rate <= 0 ||
        embedding_model_path == nullptr || window_ms <= 0 || step_ms <= 0) {
        return ToString(env, "{\"status\":\"invalid_input\",\"engine\":\"sherpa-onnx\"}");
    }
    const jsize sample_count = env->GetArrayLength(pcm);
    const int window_samples = static_cast<int>(sample_rate * window_ms / 1000LL);
    const int step_samples = static_cast<int>(sample_rate * step_ms / 1000LL);
    if (window_samples <= 0 || step_samples <= 0 || sample_count < window_samples) {
        return ToString(env, "{\"status\":\"invalid_input\",\"engine\":\"sherpa-onnx\"}");
    }
    const char* model = env->GetStringUTFChars(embedding_model_path, nullptr);
    SherpaOnnxSpeakerEmbeddingExtractorConfig config{};
    config.model = model;
    config.num_threads = 2;
    config.provider = "cpu";
    const auto* extractor = SherpaOnnxCreateSpeakerEmbeddingExtractor(&config);
    env->ReleaseStringUTFChars(embedding_model_path, model);
    if (extractor == nullptr) {
        return ToString(env, "{\"status\":\"model_load_failed\",\"engine\":\"sherpa-onnx\"}");
    }
    jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);
    const int dimension = SherpaOnnxSpeakerEmbeddingExtractorDim(extractor);
    std::string json = "{\"status\":\"ok\",\"window_ms\":" + std::to_string(window_ms) + ",\"step_ms\":" + std::to_string(step_ms) + ",\"windows\":[";
    bool first = true;
    for (int start = 0; start + window_samples <= sample_count; start += step_samples) {
        double energy = 0.0;
        for (int i = 0; i < window_samples; ++i) energy += samples[start + i] * samples[start + i];
        if (energy / window_samples < 0.00005) continue;
        const auto* stream = SherpaOnnxSpeakerEmbeddingExtractorCreateStream(extractor);
        SherpaOnnxOnlineStreamAcceptWaveform(stream, sample_rate, samples + start, window_samples);
        SherpaOnnxOnlineStreamInputFinished(stream);
        if (!SherpaOnnxSpeakerEmbeddingExtractorIsReady(extractor, stream)) {
            SherpaOnnxDestroyOnlineStream(stream);
            continue;
        }
        const float* embedding = SherpaOnnxSpeakerEmbeddingExtractorComputeEmbedding(extractor, stream);
        if (!embedding) {
            SherpaOnnxDestroyOnlineStream(stream);
            continue;
        }
        if (!first) json += ',';
        first = false;
        json += "{\"start_ms\":" + std::to_string(start * 1000LL / sample_rate) +
                ",\"end_ms\":" + std::to_string((start + window_samples) * 1000LL / sample_rate) +
                ",\"values\":[";
        for (int i = 0; i < dimension; ++i) {
            if (i > 0) json += ',';
            json += std::to_string(embedding[i]);
        }
        json += "]}";
        SherpaOnnxSpeakerEmbeddingExtractorDestroyEmbedding(embedding);
        SherpaOnnxDestroyOnlineStream(stream);
    }
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    SherpaOnnxDestroySpeakerEmbeddingExtractor(extractor);
    json += "]}";
    return ToString(env, json);
}
