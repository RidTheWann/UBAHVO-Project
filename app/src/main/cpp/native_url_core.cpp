#include <jni.h>

#include <cstddef>
#include <cstdint>

extern "C" std::int32_t ubahvo_normalize_youtube_url(
    const char* input,
    char* output,
    std::size_t output_capacity
);

extern "C" JNIEXPORT jstring JNICALL
Java_com_ridthewann_ubahvo_core_NativeUrlCore_normalizeNative(
    JNIEnv* env,
    jobject /* receiver */,
    jstring input
) {
    if (input == nullptr) {
        return nullptr;
    }

    const jsize input_length = env->GetStringUTFLength(input);
    if (input_length <= 0 || input_length > 4096) {
        return nullptr;
    }

    const char* input_chars = env->GetStringUTFChars(input, nullptr);
    if (input_chars == nullptr) {
        return nullptr;  // The JVM has already raised an allocation exception.
    }

    char output[4097] = {};
    const std::int32_t result = ubahvo_normalize_youtube_url(
        input_chars,
        output,
        sizeof(output)
    );

    env->ReleaseStringUTFChars(input, input_chars);

    if (result <= 0 || static_cast<std::size_t>(result) >= sizeof(output)) {
        return nullptr;
    }

    return env->NewStringUTF(output);
}
