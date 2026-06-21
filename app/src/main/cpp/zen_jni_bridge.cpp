/**
 * zen_jni_bridge.cpp
 *
 * JNI implementations of the external functions declared in ZenNativeActivity.kt.
 * These allow the Kotlin layer to pass context to the native layer after
 * the NativeActivity is created.
 */

#include <jni.h>
#include <string>
#include <android/log.h>
#include "zen_symbol_resolver.h"

#define LOG_TAG "ZenJniBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define ZEN_VERSION "1.0.0"

// JNI naming convention: Java_<packageName with _ for dots>_<className>_<methodName>
// Package: com.zen.client, Class: ZenNativeActivity

extern "C" {

/**
 * Receives the Bedrock native library directory from ZenNativeActivity.kt
 * and initializes the global ZenSymbolResolver with it.
 *
 * Called from Kotlin:
 *   ZenNativeActivity.nativeSetBedrockLibDir(libDir)
 */
JNIEXPORT void JNICALL
Java_com_zen_client_ZenNativeActivity_nativeSetBedrockLibDir(
        JNIEnv* env,
        jclass  /* clazz */,
        jstring libDir) {

    const char* libDirCStr = env->GetStringUTFChars(libDir, nullptr);
    if (!libDirCStr) {
        LOGE("nativeSetBedrockLibDir: failed to get string chars");
        return;
    }

    std::string libDirStr(libDirCStr);
    env->ReleaseStringUTFChars(libDir, libDirCStr);

    // Construct full path to libminecraftpe.so
    std::string bedrockLibPath = libDirStr + "/libminecraftpe.so";
    LOGI("Initializing ZenSymbolResolver with: %s", bedrockLibPath.c_str());

    // Delete old resolver if somehow called twice
    delete g_resolver;
    g_resolver = new ZenSymbolResolver(bedrockLibPath);

    LOGI("ZenSymbolResolver initialized successfully");
}

/**
 * Returns the Zen Client version string to the Kotlin layer.
 *
 * Called from Kotlin:
 *   ZenNativeActivity.nativeGetVersion()
 */
JNIEXPORT jstring JNICALL
Java_com_zen_client_ZenNativeActivity_nativeGetVersion(
        JNIEnv* env,
        jclass  /* clazz */) {
    return env->NewStringUTF(ZEN_VERSION);
}

} // extern "C"
