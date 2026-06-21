package com.zenclient.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MainActivity
 *
 * Responsibilities:
 *  1. Query PackageManager for the user's own installed Minecraft package.
 *  2. Resolve the absolute path to libminecraftpe.so inside that package's
 *     native library directory (which Android extracts to a private path on
 *     the user's device — never inside our APK).
 *  3. Call System.load(absolutePath) on the user's copy.
 *  4. Call System.loadLibrary("zenclient") to map our hook library.
 *  5. Start ZenNativeActivity, which inherits NativeActivity and hosts the
 *     combined process.
 *
 * If Minecraft is not installed, we show a clear error UI instead of
 * crashing or doing anything unexpected.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ZenClientLauncher"
        private const val MOJANG_PACKAGE = "com.mojang.minecraftpe"
        private const val BEDROCK_LIB_NAME = "libminecraftpe.so"
        private const val ZEN_LIB_NAME = "zenclient"
    }

    // -----------------------------------------------------------------------
    // UI references (set in onCreate via view binding / findViewById)
    // -----------------------------------------------------------------------
    private lateinit var statusText: TextView
    private lateinit var launchButton: Button
    private lateinit var errorContainer: View
    private lateinit var errorText: TextView
    private lateinit var installButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText    = findViewById(R.id.statusText)
        launchButton  = findViewById(R.id.launchButton)
        errorContainer = findViewById(R.id.errorContainer)
        errorText     = findViewById(R.id.errorText)
        installButton = findViewById(R.id.installButton)

        launchButton.setOnClickListener { beginLaunchSequence() }

        // Open the Play Store listing if the user taps "Get Minecraft"
        installButton.setOnClickListener {
            openPlayStoreListing()
        }

        // Auto-trigger if Minecraft is already detected at startup
        beginLaunchSequence()
    }

    // -----------------------------------------------------------------------
    // Core launch sequence
    // -----------------------------------------------------------------------

    private fun beginLaunchSequence() {
        lifecycleScope.launch {
            setUiState(UiState.LOADING("Looking for Minecraft…"))
            val result = withContext(Dispatchers.IO) { resolveAndLoad() }
            when (result) {
                is LoadResult.Success -> {
                    setUiState(UiState.LAUNCHING)
                    startZenNativeActivity()
                }
                is LoadResult.NotInstalled -> {
                    setUiState(UiState.ERROR(
                        "Minecraft isn't installed on this device.\n\n" +
                        "Zen Client Launcher requires a legitimate copy of Minecraft " +
                        "Bedrock Edition purchased through the Google Play Store.\n\n" +
                        "Tap below to get it, then come back."
                    ))
                }
                is LoadResult.LibraryNotFound -> {
                    setUiState(UiState.ERROR(
                        "Minecraft is installed but its native library couldn't be located.\n\n" +
                        "Path checked: ${result.path}\n\n" +
                        "Try reinstalling Minecraft, then relaunch."
                    ))
                }
                is LoadResult.LoadError -> {
                    setUiState(UiState.ERROR(
                        "Library load failed: ${result.message}\n\n" +
                        "This can happen if your device ABI doesn't match arm64-v8a. " +
                        "Please report this on the Zen Client GitHub with the above error."
                    ))
                }
            }
        }
    }

    /**
     * Runs on IO dispatcher.
     *
     * Step 1 — PackageManager query
     * We use GET_SHARED_LIBRARY_FILES so ApplicationInfo.nativeLibraryDir is
     * populated (some OEMs strip it without this flag).
     *
     * Step 2 — Library path construction
     * nativeLibraryDir is the ABI-specific subdirectory that the Android
     * package installer already extracted the .so files into.  On arm64 devices
     * this is typically something like:
     *   /data/app/~~randomhash==/com.mojang.minecraftpe-hash==/lib/arm64/
     * The exact path is opaque to us — we just ask ApplicationInfo for it.
     *
     * Step 3 — System.load() with absolute path
     * We use System.load(absolutePath) rather than System.loadLibrary(name)
     * because the latter only searches our own APK's lib directory, not an
     * arbitrary path.  System.load() accepts any absolute path on the
     * filesystem that the current process can read.
     *
     * Step 4 — System.loadLibrary("zenclient")
     * Our own library IS in our APK's lib/arm64-v8a/ directory, so the
     * standard loadLibrary lookup finds it normally.
     */
    private fun resolveAndLoad(): LoadResult {
        // --- Step 1: Find Minecraft's ApplicationInfo ---
        val appInfo = try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(
                MOJANG_PACKAGE,
                PackageManager.GET_SHARED_LIBRARY_FILES
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Minecraft not found on device", e)
            return LoadResult.NotInstalled
        }

        // --- Step 2: Construct absolute path to libminecraftpe.so ---
        val nativeDir = appInfo.nativeLibraryDir
            ?: return LoadResult.LibraryNotFound("appInfo.nativeLibraryDir was null")

        val bedrockLibPath = File(nativeDir, BEDROCK_LIB_NAME).absolutePath
        Log.d(TAG, "Bedrock native lib path: $bedrockLibPath")

        if (!File(bedrockLibPath).exists()) {
            Log.e(TAG, "libminecraftpe.so not found at: $bedrockLibPath")
            return LoadResult.LibraryNotFound(bedrockLibPath)
        }

        // --- Step 3 & 4: Load both libraries ---
        return try {
            Log.i(TAG, "Loading user's Bedrock library from: $bedrockLibPath")
            System.load(bedrockLibPath)          // user's copy of Bedrock — never from our APK
            Log.i(TAG, "Loading Zen hook library")
            System.loadLibrary(ZEN_LIB_NAME)     // our libzenclient.so from our APK's lib/arm64-v8a/
            Log.i(TAG, "Both libraries loaded successfully")
            LoadResult.Success
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Library load failed", e)
            LoadResult.LoadError(e.message ?: "UnsatisfiedLinkError (no message)")
        }
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    private fun startZenNativeActivity() {
        val intent = Intent(this, ZenNativeActivity::class.java)
        // Pass the Bedrock lib path so the native side can use it if needed
        // (e.g. for dlopen calls — see ZenNativeActivity)
        val appInfo = packageManager.getApplicationInfo(MOJANG_PACKAGE, 0)
        intent.putExtra("bedrock_native_dir", appInfo.nativeLibraryDir)
        startActivity(intent)
        finish()    // don't keep MainActivity in the back stack during gameplay
    }

    private fun openPlayStoreListing() {
        val intent = Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("market://details?id=$MOJANG_PACKAGE")
        )
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback: open in browser if Play Store app not available
            startActivity(Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://play.google.com/store/apps/details?id=$MOJANG_PACKAGE")
            ))
        }
    }

    // -----------------------------------------------------------------------
    // UI state machine
    // -----------------------------------------------------------------------

    private sealed class UiState {
        data class LOADING(val message: String) : UiState()
        data class ERROR(val message: String) : UiState()
        object LAUNCHING : UiState()
    }

    private fun setUiState(state: UiState) {
        runOnUiThread {
            when (state) {
                is UiState.LOADING -> {
                    statusText.text = state.message
                    launchButton.isEnabled = false
                    errorContainer.visibility = View.GONE
                }
                is UiState.ERROR -> {
                    statusText.text = ""
                    launchButton.isEnabled = true
                    errorContainer.visibility = View.VISIBLE
                    errorText.text = state.message
                }
                UiState.LAUNCHING -> {
                    statusText.text = "Launching…"
                    launchButton.isEnabled = false
                    errorContainer.visibility = View.GONE
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Result types
    // -----------------------------------------------------------------------

    private sealed class LoadResult {
        object Success : LoadResult()
        object NotInstalled : LoadResult()
        data class LibraryNotFound(val path: String) : LoadResult()
        data class LoadError(val message: String) : LoadResult()
    }
}
