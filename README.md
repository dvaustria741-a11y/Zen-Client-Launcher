# Zen Client Launcher

An Android launcher for Minecraft Bedrock Edition that layers `libzenclient.so` (a custom hook library) on top of the user's own legitimately-installed copy of Minecraft.

## Architecture

This project uses the **user-supplied-binary pattern**, identical in architecture to [mcpelauncher-manifest](https://github.com/minecraft-linux/mcpelauncher-manifest), Flarial Launcher, and Latite Recovery.

**This APK contains zero Mojang code, zero FMOD code, and zero Minecraft assets.**

At runtime:
1. `PackageManager` queries the user's installed `com.mojang.minecraftpe` package.
2. `applicationInfo.nativeLibraryDir` resolves the user's own `libminecraftpe.so` on their device.
3. `System.load(absolutePath)` loads the user's copy (never from our APK).
4. `System.loadLibrary("zenclient")` loads our hook library on top in the same process.
5. `ZenNativeActivity` (a `NativeActivity` subclass) hosts the combined process.

If Minecraft is not installed, the launcher shows an error and prompts the user to get it from the Play Store.

## Requirements

- Android 8.0+ (API 26)
- arm64-v8a device
- Minecraft Bedrock Edition installed from Google Play

## Build

```bash
./gradlew assembleDebug
```

Requires Android NDK r25+ and CMake 3.22.1+.

## License

MIT
