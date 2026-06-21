# ZenOverlay

Route A: a standalone floating-overlay HUD for Minecraft Bedrock on Android.
Does **not** touch Minecraft's process or files — it's a separate app that
draws on top using the `SYSTEM_ALERT_WINDOW` overlay permission.

## What's here (v0.1 skeleton)

- **MainActivity** — checks/requests overlay permission, checks Minecraft
  Bedrock is installed, launches it, starts the overlay service.
- **OverlayService** — foreground service that draws:
  - a draggable bubble (tap to open/close the menu)
  - a ClickGUI-style toggle panel
  - an FPS counter module (first toggle in the panel)

## FPS counter — read this before you rely on it

The FPS number comes from `Choreographer.postFrameCallback`, which ticks on
the display's vsync signal. That's a solid proxy for overall device frame
pacing, but it is **not** pulled from Minecraft's internal renderer — there's
no process injection in Route A. If you need the *actual* number Minecraft
itself is rendering at, that requires Route B (patching the Bedrock APK to
load a module inside its process, e.g. via LSPatch) — a much bigger step.

## Building

No `gradlew` wrapper is checked in yet (couldn't generate the wrapper jar in
this sandbox — no network path to the Gradle distribution). Two options:

1. **Open in Android Studio** — it will offer to generate the wrapper for
   you on first sync. Just accept it.
2. **GitHub Actions** (`.github/workflows/build.yml`) — installs Gradle 8.7
   directly on the runner and builds via `gradle assembleDebug`, no wrapper
   needed. Push to `main` or run it manually from the Actions tab; grab the
   APK from the workflow's build artifacts.

## Known gaps / next steps

- No app icon yet (using the framework default placeholder).
- Panel only has the FPS toggle — add modules as switches in
  `OverlayService.toggleGui()`.
- Minecraft version isn't pinned anywhere; it just launches whatever's
  installed (e.g. 1.26.30 / 26.30).
- No settings persistence — toggles reset each time the service restarts.
