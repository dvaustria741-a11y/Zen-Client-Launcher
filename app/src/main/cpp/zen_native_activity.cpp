/**
 * zen_native_activity.cpp
 *
 * Overrides ANativeActivity_onCreate, which is the first C function the
 * Android framework calls after NativeActivity's Java onCreate() triggers
 * System.loadLibrary("zenclient").
 *
 * EXECUTION ORDER
 * ───────────────
 * 1. ZenNativeActivity.super.onCreate() (Kotlin)
 * 2. Android framework: calls ANativeActivity_onCreate(activity, ...)
 *    → This resolves to OUR function below because:
 *      a. libzenclient.so was loaded AFTER libminecraftpe.so.
 *      b. ANativeActivity_onCreate is a weak symbol in the NDK;
 *         our definition wins because it's non-weak and was loaded last.
 *      c. The dynamic linker uses the last definition seen for non-weak symbols
 *         when multiple DSOs export the same name.
 * 3. We install our hooks (see zen_hook_installer.cpp).
 * 4. We call the real Bedrock ANativeActivity_onCreate via a saved function
 *    pointer that zen_symbol_resolver found via dlsym before we trampolined it.
 * 5. Bedrock's own init runs — by this point our hooks are already in place.
 */

#include <android/native_activity.h>
#include <android/log.h>
#include "zen_hook_installer.h"
#include "zen_symbol_resolver.h"

#define LOG_TAG "ZenClient"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Forward declaration: defined in zen_symbol_resolver.cpp
// ---------------------------------------------------------------------------
extern ZenSymbolResolver* g_resolver;

// Pointer to Bedrock's real ANativeActivity_onCreate, saved before we run
// so we can call it in step 4 above.
using NativeActivityOnCreate_t = void (*)(ANativeActivity*, void*, size_t);
static NativeActivityOnCreate_t s_bedrockOnCreate = nullptr;

// ---------------------------------------------------------------------------
// Our ANativeActivity_onCreate — the framework calls this first.
//
// Marked __attribute__((visibility("default"))) explicitly even though the
// NativeActivity framework requires a default-visibility export.
// ---------------------------------------------------------------------------
extern "C" __attribute__((visibility("default")))
void ANativeActivity_onCreate(ANativeActivity* activity,
                              void*            savedState,
                              size_t           savedStateSize) {
    LOGI("=== ZenClient ANativeActivity_onCreate ===");

    // ------------------------------------------------------------------
    // Step 1: Resolve Bedrock's own ANativeActivity_onCreate via dlsym
    //         so we can call it after our hooks are in.
    //
    //         RTLD_DEFAULT searches all already-mapped DSOs in load order.
    //         Since libminecraftpe.so was mapped first (MainActivity.kt
    //         called System.load() before System.loadLibrary("zenclient")),
    //         dlsym(RTLD_DEFAULT, "ANativeActivity_onCreate") would return
    //         US, not Bedrock, because we're the later-loaded definition.
    //
    //         To get Bedrock's version, we use RTLD_NEXT, which returns
    //         the *next* definition after the DSO containing this call site
    //         (i.e. the definition in a DSO loaded before us = Bedrock).
    // ------------------------------------------------------------------
    s_bedrockOnCreate = reinterpret_cast<NativeActivityOnCreate_t>(
        dlsym(RTLD_NEXT, "ANativeActivity_onCreate")
    );

    if (!s_bedrockOnCreate) {
        LOGE("Could not locate Bedrock's ANativeActivity_onCreate via RTLD_NEXT. "
             "dlsym error: %s", dlerror());
        // Non-fatal for now — some Bedrock versions may use a different entry
        // point name.  Let hook installation proceed anyway.
    } else {
        LOGI("Located Bedrock ANativeActivity_onCreate at %p", (void*)s_bedrockOnCreate);
    }

    // ------------------------------------------------------------------
    // Step 2: Install hooks.
    //         At this point both libminecraftpe.so and libzenclient.so are
    //         fully mapped into this process's address space.  All of
    //         Bedrock's exported symbols are reachable via dlsym / our
    //         ZenSymbolResolver, and inline patches can be written to the
    //         Bedrock .text segment (after mprotect to make it writable).
    // ------------------------------------------------------------------
    ZenHookInstaller::installAll();

    // ------------------------------------------------------------------
    // Step 3: Hand off to Bedrock's entry point.
    //         Bedrock performs its own NativeActivity callback setup here.
    //         Our hooks are already installed, so any callbacks Bedrock
    //         registers that we've also hooked will run our code first.
    // ------------------------------------------------------------------
    if (s_bedrockOnCreate) {
        LOGI("Handing off to Bedrock ANativeActivity_onCreate");
        s_bedrockOnCreate(activity, savedState, savedStateSize);
    }

    LOGI("=== ZenClient init complete ===");
}
