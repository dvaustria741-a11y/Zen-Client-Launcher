cmake_minimum_required(VERSION 3.22.1)
project(ZenClient VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Compiler hardening flags
# ---------------------------------------------------------------------------
add_compile_options(
    -fvisibility=hidden         # Only explicitly marked symbols are exported
    -fstack-protector-strong
    -D_FORTIFY_SOURCE=2
    -Wno-unused-command-line-argument
)

# ---------------------------------------------------------------------------
# Android system libraries (provided by NDK — no need to link them from disk)
# ---------------------------------------------------------------------------
find_library(log-lib       log)
find_library(android-lib   android)
find_library(egl-lib       EGL)
find_library(gles2-lib     GLESv2)
find_library(dl-lib        dl)

# ---------------------------------------------------------------------------
# libzenclient.so — our hook / launcher library
#
# This shared library is the ONLY native library we distribute.
# It contains:
#   • ANativeActivity_onCreate — replaces NativeActivity's default entry point
#   • JNI bridge functions called from ZenNativeActivity.kt
#   • Symbol resolver that uses dlsym against the already-mapped Bedrock lib
#   • Hook stubs for any Bedrock symbols we patch at runtime
# ---------------------------------------------------------------------------
add_library(
    zenclient
    SHARED
    # Entry points
    zen_native_activity.cpp       # ANativeActivity_onCreate override
    zen_jni_bridge.cpp            # JNI: nativeSetBedrockLibDir, nativeGetVersion
    # Core
    zen_symbol_resolver.cpp       # dlopen / dlsym wrapper utilities
    zen_hook_installer.cpp        # Installs inline hooks into Bedrock symbol slots
    # Optional client modules (add yours here)
    # modules/zen_esp.cpp
    # modules/zen_click_gui.cpp
)

# ---------------------------------------------------------------------------
# Public API visibility — only ANativeActivity_onCreate and the JNI functions
# need to be exported; everything else stays hidden behind -fvisibility=hidden.
# ---------------------------------------------------------------------------
set_target_properties(zenclient PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# ---------------------------------------------------------------------------
# Link against Android system libraries
# ---------------------------------------------------------------------------
target_link_libraries(
    zenclient
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles2-lib}
    ${dl-lib}
    # android_native_app_glue is a static helper from the NDK that connects
    # the NativeActivity lifecycle callbacks to our code.  It ships with the
    # NDK so we just add_library it as STATIC IMPORTED.
    android_native_app_glue
)

# ---------------------------------------------------------------------------
# NDK native app glue (static, provided by NDK)
# ---------------------------------------------------------------------------
add_library(android_native_app_glue STATIC
    ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c
)
target_include_directories(android_native_app_glue PUBLIC
    ${ANDROID_NDK}/sources/android/native_app_glue
)
target_include_directories(zenclient PRIVATE
    ${ANDROID_NDK}/sources/android/native_app_glue
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)
