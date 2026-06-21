/**
 * zen_hook_installer.cpp
 *
 * Central registry of all hooks.
 * Add your module hook install calls here.
 *
 * Each module is responsible for resolving its own symbols via g_resolver
 * and installing its patches.  This file just orchestrates the order.
 */

#include "zen_hook_installer.h"
#include "zen_symbol_resolver.h"
#include <android/log.h>

#define LOG_TAG "ZenHookInstaller"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

namespace ZenHookInstaller {

void installAll() {
    LOGI("Installing hooks...");

    // --- Add your module hook installers here ---
    // e.g.:
    // EspModule::install(g_resolver);
    // ClickGuiModule::install(g_resolver);
    // AutoSoupModule::install(g_resolver);

    LOGI("Hook installation complete.");
}

} // namespace ZenHookInstaller
