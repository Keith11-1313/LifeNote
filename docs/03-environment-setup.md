# 03 — Environment Setup (Dev PC: Windows)

One-time setup, then permanent capability: the PC compiles APKs offline from here on. Gradle caches everything on first build; no internet is required afterward.

> **Android Studio and an emulator are not required for this project.** The established workflow uses the command-line SDK, browser mock mode, and a physical Android device, avoiding a large IDE and virtual-device image.

## Current status on this PC (verified 2026-09-05)

| Component | State |
|---|---|
| JDK 17 (Temurin 17.0.12) | ✅ present — `JAVA_HOME` points at `C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot` |
| Android SDK cmdline-tools | ✅ installed at `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` |
| SDK platform + build-tools | ✅ installed — platform-tools 37.0.1, platforms;android-35, build-tools;35.0.0 |
| GitHub CLI 2.98.0 (`gh`) | ⚠️ installed; the saved token is invalid and `gh auth login` must run before the next push or release |

**Environment is complete — all steps below are reference for rebuild-on-a-new-machine scenarios only.**

## Step 2b — Install GitHub CLI

```powershell
winget install --id GitHub.cli --exact --silent --accept-source-agreements --accept-package-agreements
```

Then authenticate (interactive, once):

```powershell
gh auth login    # GitHub.com → HTTPS → Yes → Login with a web browser
```

Verification:

```powershell
gh auth status                    # ✓ Logged in to github.com
git ls-remote origin              # read-only repository access check
```

## What gets installed

| Tool | What it is | Size |
|---|---|---|
| **JDK 17** (Temurin) | The compiler that turns Kotlin into runnable bytecode | ~300 MB |
| **Android SDK Command-line Tools** | Google's downloader for SDK parts (`sdkmanager`) | ~100 MB |
| **SDK Platform + Build-Tools** | Android's libraries + packaging tools (pulled by `sdkmanager`) | ~500 MB |
| **GitHub CLI (`gh`)** | Terminal access to GitHub: auth, releases (APK hosting), repo management | ~50 MB |
| **Gradle** | Build automation — downloaded *automatically* by the wrapper on first build | ~130 MB |

The command-line Android toolchain uses roughly 1 GB plus Gradle caches. The JDK may be installed system-wide; no Android Studio installation or emulator image is required.

## Step 1 — Install JDK 17

```powershell
# Check if a JDK is already present
java -version
```

If missing or < 17, install Eclipse Temurin 17 via winget:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Verify (open a **new** terminal first so PATH refreshes):

```powershell
java -version    # expect: openjdk version "17.x.x"
```

## Step 2 — Install Android SDK command-line tools

```powershell
# Create the SDK home
New-Item -ItemType Directory -Force -Path "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools"

# Download the tools zip (pinned version — change only with a doc update)
$zip = "$env:TEMP\cmdline-tools.zip"
Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip

# Unzip into place (folder must be named "latest")
Expand-Archive -Path $zip -DestinationPath "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools"
Rename-Item "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\cmdline-tools" "latest"
```

## Step 3 — Pull the SDK packages

```powershell
$sdkmanager = "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat"

# Accept licenses, then install what we need:
& $sdkmanager --licenses
& $sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

| Package | Why |
|---|---|
| `platform-tools` | `adb` — lets a connected phone receive the APK over USB for testing |
| `platforms;android-35` | Android 15 API definitions to compile against |
| `build-tools;35.0.0` | `aapt2`, `d8`, `apksigner` — the packaging line |

## Step 4 — Tell the tools where the SDK lives

Create `local.properties` in the repository root using the current Windows account name:

```properties
sdk.dir=C\:\\Users\\<Windows-user>\\AppData\\Local\\Android\\Sdk
```

Replace `<Windows-user>` with the local account folder name. `local.properties` is gitignored because this path is machine-specific. Optionally set `ANDROID_HOME` to `%LOCALAPPDATA%\Android\Sdk` for tools that use the environment variable.

## Step 5 — Verify everything

```powershell
java -version                                    # 17.x
& $sdkmanager --list_installed                   # 3 packages above
adb version                                      # Android Debug Bridge
```

All three answering = Android build environment ready. GitHub publishing additionally requires `gh auth status` to report a valid login.

## Maintenance cost of this setup: zero

- The SDK never auto-updates or breaks itself
- Builds work offline once Gradle has cached dependencies
- If the PC dies: this doc rebuilds the environment in ~20 minutes on any Windows machine

## Release-build behavior

The release build uses Android's bundled R8 optimizer and resource shrinker; it adds no runtime dependency. When `app\keystore.jks` exists and `LIFENOTE_KEYSTORE_PASSWORD` is set, Gradle signs the optimized APK with alias `lifenote`. Without both, `assembleRelease` deliberately produces an unsigned verification artifact.
