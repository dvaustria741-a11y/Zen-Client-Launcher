package com.zenclient.launcher

import android.app.NativeActivity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * ZenNativeActivity
 *
 * A thin Kotlin subclass of NativeActivity whose sole purpose is to:
 *  1. Configure the window (fullscreen, keep-screen-on, hide system bars).
 *  2. Pass the Bedrock native library directory to native code via a JNI call
 *     so our hook layer can use dlopen/dlsym against it if needed after init.
 *  3. Do nothing else — all rendering, input, and game logic is handled by
 *     native code in libzenclient.so.
 *
 * The actual ANativeActivity_onCreate interception happens on the C++ side
 * (see zen_native_activity.cpp).  By the time super.onCreate() returns here,
 * our native init function has already run.
 *
 * WHY SUBCLASS INSTEAD OF USING PLAIN NativeActivity?
 * - We need to pass extra context (the Bedrock lib path) from the Java layer
 *   to native before the game loop starts.
 * - We want to configure window flags that are cleaner to set from Kotlin
 *   than from native code via JNI round-trips.
 * - android:hasCode="false" in the manifest means the Android framework would
 *   otherwise call NativeActivity directly with no Java interception.  Setting
 *   android:hasCode="true" (implicitly, by having this Kotlin class) lets us
 *   intercept onCreate.
 */
class ZenNativeActivity : NativeActivity() {

    companion object {
        private const val TAG = "ZenNativeActivity"

        // JNI bridge — implemented in src/main/cpp/zen_jni_bridge.cpp
        @JvmStatic
        external fun nativeSetBedrockLibDir(libDir: String)

        @JvmStatic
        external fun nativeGetVersion(): String
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply window flags before super.onCreate() so they're in place
        // before the native surface is created.
        configureWindow()

        // super.onCreate() triggers ANativeActivity_onCreate in our native lib,
        // which installs all hooks before Bedrock's own entry point runs.
        super.onCreate(savedInstanceState)

        // Pass the Bedrock native lib directory to our native layer.
        // The native side uses this to dlopen libminecraftpe.so for symbol
        // resolution if needed (see zen_symbol_resolver.cpp).
        val bedrockNativeDir = intent.getStringExtra("bedrock_native_dir") ?: run {
            // Fallback: re-resolve from PackageManager in the rare case the
            // intent extra was stripped (e.g., process restart).
            try {
                packageManager
                    .getApplicationInfo("com.mojang.minecraftpe", 0)
                    .nativeLibraryDir
            } catch (e: Exception) {
                Log.e(TAG, "Could not resolve Bedrock native dir in ZenNativeActivity", e)
                null
            }
        }

        bedrockNativeDir?.let {
            Log.d(TAG, "Passing Bedrock native dir to native layer: $it")
            nativeSetBedrockLibDir(it)
        }

        Log.i(TAG, "ZenNativeActivity started — native version: ${nativeGetVersion()}")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-apply immersive mode each time focus returns (system bar auto-hide
        // resets when the user swipes from the edge).
        if (hasFocus) applyImmersiveMode()
    }

    // -----------------------------------------------------------------------
    // Window configuration
    // -----------------------------------------------------------------------

    private fun configureWindow() {
        // Keep the screen on during gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Landscape — Bedrock only renders in landscape on Android
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        // API 30+ — use WindowInsetsController
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // API 26–29 fallback
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
}
