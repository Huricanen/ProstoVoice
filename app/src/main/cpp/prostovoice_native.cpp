#include <jni.h>

namespace {

jstring ToString(JNIEnv* env, const char* value) {
    return env->NewStringUTF(value);
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeBackendStatus(
        JNIEnv* env,
        jobject /* self */) {
    return ToString(env, "native_stub");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeTranscribePcm(
        JNIEnv* env,
        jobject /* self */,
        jfloatArray /* pcm */,
        jint /* sample_rate */,
        jstring /* model_path */) {
    // Whisper.cpp integration is deliberately isolated behind this contract.
    return ToString(env, "{\"status\":\"not_configured\",\"engine\":\"whisper.cpp\"}");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prostovoice_nativebridge_NativePipeline_nativeDiarizePcm(
        JNIEnv* env,
        jobject /* self */,
        jfloatArray /* pcm */,
        jint /* sample_rate */,
        jstring /* segmentation_model_path */,
        jstring /* embedding_model_path */) {
    // sherpa-onnx integration will be added after model compatibility is fixed.
    return ToString(env, "{\"status\":\"not_configured\",\"engine\":\"sherpa-onnx\"}");
}
