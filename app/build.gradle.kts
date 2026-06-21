plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zen.client"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zen.client"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // ---------------------------------------------------------------
        // NDK: arm64-v8a only — the only ABI Bedrock ships on real devices
        // ---------------------------------------------------------------
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_ABI=arm64-v8a",
                    "-DANDROID_PLATFORM=android-26",
                    "-DANDROID_STL=c++_shared",
                    "-DZEN_BUILD_HOOK_LIB=ON"
                )
                cppFlags("-std=c++17 -fvisibility=hidden -O2")
            }
        }
    }

    // -----------------------------------------------------------------------
    // CMake build for libzenclient.so
    // -----------------------------------------------------------------------
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isDebuggable = true
            isJniDebuggable = true
        }
    }

    // -----------------------------------------------------------------------
    // Packaging: hard-exclude any accidental Mojang / FMOD .so inclusion.
    // Our APK distributes ONLY libzenclient.so.
    // -----------------------------------------------------------------------
    packaging {
        jniLibs {
            excludes += listOf(
                "**/libminecraftpe.so",
                "**/libfmod.so",
                "**/libfmodL.so",
                "**/libminecraft*.so",
                // We pull in org.conscrypt:conscrypt-android below purely for its
                // Java-side classes (org.conscrypt.CryptoUpcalls etc.) — Mojang's
                // own libconscrypt_jni.so is what we explicitly System.load() from
                // Bedrock's native lib dir in loadBedrockNativeLibs(), so we don't
                // want this artifact's bundled .so shadowing/conflicting with it.
                "**/libconscrypt_jni.so"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Bedrock's bundled libconscrypt_jni.so (loaded explicitly in
    // ZenNativeActivity.loadBedrockNativeLibs) does a JNI FindClass +
    // NewGlobalRef on org.conscrypt.CryptoUpcalls during load. That class only
    // ships inside Minecraft's own APK dex, not ours, so the lookup throws
    // ClassNotFoundException — and since the native code doesn't check for a
    // pending exception before NewGlobalRef, it hard-aborts (SIGABRT) under
    // strict JNI checking. Pulling in the standalone Conscrypt artifact gives
    // our own APK the matching Java-side classes so the lookup succeeds.
    // (Its bundled native .so is excluded above — we keep using Mojang's own.)
    implementation("org.conscrypt:conscrypt-android:2.5.2")
}
