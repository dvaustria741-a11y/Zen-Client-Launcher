package com.zen.client

import android.app.NativeActivity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import dalvik.system.PathClassLoader

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

        // NativeActivity maps libzenclient.so via its own internal dlopen()
        // (for android.app.lib_name / ANativeActivity_onCreate), which does
        // NOT register the library with the JNI native-method resolver.
        // Without this explicit load, external fun below throws
        // UnsatisfiedLinkError even though the .so is already in memory and
        // the symbol names are correct. Safe to call even though the lib is
        // already mapped — System.loadLibrary just registers it for lookup.
        init {
            System.loadLibrary("zenclient")
        }

        // JNI bridge — implemented in src/main/cpp/zen_jni_bridge.cpp
        @JvmStatic
        external fun nativeSetBedrockLibDir(libDir: String)

        @JvmStatic
        external fun nativeGetVersion(): String
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Resolve Bedrock's native lib dir FIRST and System.load() it before
        // super.onCreate(). super.onCreate() is what triggers the framework's
        // internal dlopen("libzenclient.so"), which calls our hijacked
        // ANativeActivity_onCreate (zen_native_activity.cpp). That function
        // uses dlsym(RTLD_NEXT, "ANativeActivity_onCreate") to find Bedrock's
        // real entry point and hand off to it — but RTLD_NEXT only sees
        // libraries already mapped into the process at that moment. If
        // libminecraftpe.so isn't loaded yet, the lookup fails silently,
        // Bedrock's entry point never runs, and nothing ever renders —
        // which is exactly the black screen we were hitting. Loading it here,
        // before configureWindow()/super.onCreate(), guarantees it's mapped
        // in time. (Also fixes ZenSymbolResolver's later RTLD_NOLOAD lookup
        // in nativeSetBedrockLibDir, which has the same "must already be
        // loaded" requirement.)
        val bedrockNativeDir = intent.getStringExtra("bedrock_native_dir") ?: run {
            try {
                packageManager
                    .getApplicationInfo("com.mojang.minecraftpe", 0)
                    .nativeLibraryDir
            } catch (e: Exception) {
                Log.e(TAG, "Could not resolve Bedrock native dir in ZenNativeActivity", e)
                null
            }
        }

        bedrockNativeDir?.let { dir ->
            // Bedrock's bundled native libs (libfmod.so, libconscrypt_jni.so,
            // libPlayFabMultiplayer.so, etc.) do JNI FindClass lookups against
            // Java glue classes (org.fmod.FMOD, PlayFab SDK classes, media
            // decoder helpers...) that only exist inside Minecraft's own APK
            // dex — not ours. Rather than chasing each one down individually
            // (conscrypt was the first; FMOD is next), merge Minecraft's own
            // APK into our classloader first so all of these resolve straight
            // from Mojang's already-correct implementation.
            try {
                val minecraftApkPath = packageManager
                    .getApplicationInfo("com.mojang.minecraftpe", 0)
                    .sourceDir
                mergeMinecraftClasspath(minecraftApkPath)
            } catch (e: Exception) {
                Log.e(TAG, "Could not resolve Minecraft APK path for classpath merge", e)
            }
            loadBedrockNativeLibs(dir)
        } ?: Log.e(TAG, "No Bedrock native dir available — cannot load libminecraftpe.so")

        // Flags/orientation don't touch the DecorView, so they're safe to set
        // before super.onCreate().
        configureWindow()

        // super.onCreate() triggers ANativeActivity_onCreate in our native lib,
        // which installs all hooks before Bedrock's own entry point runs. It
        // also creates the DecorView, which applyImmersiveMode() needs — call
        // it after super.onCreate(), not before, or window.insetsController
        // resolves against a null DecorView and crashes.
        super.onCreate(savedInstanceState)

        applyImmersiveMode()

        // Pass the Bedrock native lib directory to our native layer so
        // ZenSymbolResolver can RTLD_NOLOAD it (it's already mapped above).
        bedrockNativeDir?.let {
            Log.d(TAG, "Passing Bedrock native dir to native layer: $it")
            nativeSetBedrockLibDir(it)
        }

        Log.i(TAG, "ZenNativeActivity started — native version: ${nativeGetVersion()}")

        // DEBUG: dump this process's own logcat (Java + native LOGI/LOGE,
        // since the hook .so logs under the same process/UID) to a plain
        // file we can read without root, adb, or a PC. Delayed 4s so native
        // init/hook-install logs land in the buffer before we dump it.
        // Remove once the black screen is sorted.
        Handler(Looper.getMainLooper()).postDelayed(
            { LogUtils.dumpLogcatToFile(this, "zen_native_activity") },
            4000
        )
    }

    // Bedrock's lib dir besides libminecraftpe.so itself — these are Mojang's
    // own bundled third-party SDKs (FMOD audio, PlayFab, Conscrypt, the MS
    // HttpClient, media decoders). libminecraftpe.so's ELF NEEDED entries
    // list these by bare soname, and the dynamic linker resolves that via
    // a per-app namespace path search — which is restricted to *our* app's
    // own lib dir, not Minecraft's. Loading each one explicitly by its
    // absolute path here first means the linker finds a same-soname library
    // already mapped when libminecraftpe.so asks for it, so it reuses that
    // instead of needing to search any path at all.
    private val bedrockDependencyLibs = listOf(
        "libc++_shared.so",
        "libconscrypt_jni.so",
        "libHttpClient.Android.so",
        "libfmod.so",
        "libmaesdk.so",
        "libMediaDecoders_Android.so",
        "libPlayFabMultiplayer.so"
    )

    // -----------------------------------------------------------------------
    // Classpath merge — gives our own classloader access to every class
    // bundled inside Minecraft's APK, so JNI FindClass calls made by Bedrock's
    // native libs (org.fmod.FMOD, PlayFab SDK classes, media decoder helpers,
    // etc.) succeed instead of throwing ClassNotFoundException.
    //
    // Reflectively reads our own PathClassLoader's DexPathList.dexElements,
    // builds a second DexPathList over Minecraft's base.apk (via a throwaway
    // PathClassLoader using our own loader as parent, so class identity for
    // shared framework/Kotlin types stays consistent), and appends Minecraft's
    // elements onto ours. Our own elements stay first in the array, so any
    // class we already ship (e.g. our conscrypt-android dependency) still
    // wins on a name clash — Minecraft's copy is purely a fallback for
    // classes we don't have at all.
    //
    // This relies on non-SDK fields (pathList / dexElements) that aren't
    // guaranteed across Android versions/OEMs. If it throws, we log and fall
    // through — loadBedrockNativeLibs() still runs, it just may hit the same
    // class of crash again for whichever lib needed the missing class.
    // -----------------------------------------------------------------------
    private fun mergeMinecraftClasspath(minecraftApkPath: String) {
        try {
            val appClassLoader = this.classLoader
            val appPathList = getPathList(appClassLoader)
            val appElements = getDexElements(appPathList)

            val minecraftClassLoader = PathClassLoader(minecraftApkPath, appClassLoader)
            val minecraftPathList = getPathList(minecraftClassLoader)
            val minecraftElements = getDexElements(minecraftPathList)

            val combined = java.lang.reflect.Array.newInstance(
                appElements.javaClass.componentType, appElements.size + minecraftElements.size
            )
            System.arraycopy(appElements, 0, combined, 0, appElements.size)
            System.arraycopy(minecraftElements, 0, combined, appElements.size, minecraftElements.size)

            setDexElements(appPathList, combined)
            Log.d(TAG, "Merged Minecraft's classpath in (${minecraftElements.size} dex elements) from $minecraftApkPath")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to merge Minecraft's classpath — Bedrock native callbacks needing Mojang's Java classes may still crash", e)
        }
    }

    private fun getPathList(classLoader: ClassLoader): Any {
        val field = findField(classLoader.javaClass, "pathList")
        return field.get(classLoader)!!
    }

    private fun getDexElements(pathList: Any): Array<Any?> {
        val field = findField(pathList.javaClass, "dexElements")
        @Suppress("UNCHECKED_CAST")
        return field.get(pathList) as Array<Any?>
    }

    private fun setDexElements(pathList: Any, elements: Any) {
        val field = findField(pathList.javaClass, "dexElements")
        field.set(pathList, elements)
    }

    private fun findField(start: Class<*>, name: String): java.lang.reflect.Field {
        var current: Class<*>? = start
        while (current != null) {
            try {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                return field
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun loadBedrockNativeLibs(dir: String) {
        // We don't know the exact inter-dependency order of these (e.g.
        // libmaesdk might itself need libfmod), so retry in passes — a lib
        // that fails in one pass may succeed once another lib loaded later
        // in that same pass satisfies its own NEEDED entry.
        var remaining = bedrockDependencyLibs.toMutableList()
        var lastError: UnsatisfiedLinkError? = null

        repeat(4) {
            if (remaining.isEmpty()) return@repeat
            val stillFailing = mutableListOf<String>()
            for (lib in remaining) {
                try {
                    System.load("$dir/$lib")
                    Log.d(TAG, "Loaded Bedrock dependency: $lib")
                } catch (e: UnsatisfiedLinkError) {
                    stillFailing.add(lib)
                    lastError = e
                }
            }
            remaining = stillFailing
        }

        if (remaining.isNotEmpty()) {
            Log.e(TAG, "Could not load some Bedrock dependencies after retries: $remaining", lastError)
        }

        try {
            System.load("$dir/libminecraftpe.so")
            Log.d(TAG, "Loaded libminecraftpe.so from $dir")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libminecraftpe.so from $dir — Bedrock hijack will not work", e)
        }
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
