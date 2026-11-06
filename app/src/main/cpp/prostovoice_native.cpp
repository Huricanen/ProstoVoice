#include <jni.h>
#include <string>

namespace {

jstring ToString(JNIEnv* env, const char* value) {
    return env->NewStringUTF(value);
}

jstring ToString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeBackendStatus(
        JNIEnv* env,
        jobject /* self */) {
    return ToString(env,
                    "{\"status\":\"stub\",\"offline\":true,"
                    "\"whisper\":\"not_configured\","
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
    // The real Whisper.cpp context is intentionally injected at the next native stage.
    return ToString(env, "{\"status\":\"not_configured\",\"engine\":\"whisper.cpp\",\"offline\":true}");
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
