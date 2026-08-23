# 03 — Environment Setup (Dev PC: Windows)

One-time setup, then permanent capability: the PC compiles APKs offline from here on. Gradle caches everything on first build; no internet is required afterward.

> **Android Studio is not used in this project.** It is a 3+ GB IDE for visual tooling we don't need. Only the command-line ingredients are installed.

## Current status on this PC (verified 2026-08-23)

| Component | State |
|---|---|
| JDK 17 (Temurin 17.0.12) | ✅ present — `JAVA_HOME` points at `C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot` |
| Android SDK cmdline-tools | ✅ installed at `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` |
| SDK platform + build-tools | ✅ installed — platform-tools 37, platforms;android-35, build-tools;35.0.0 |
| GitHub CLI 2.98.0 (`gh`) | ✅ installed, authenticated as `Keith11-1313` (scopes: repo, workflow); remote `origin` = `Keith11-1313/LifeNote`, branch `main` |

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
git push origin main              # must succeed without password prompts
```

## What gets installed

| Tool | What it is | Size |
|---|---|---|
| **JDK 17** (Temurin) | The compiler that turns Kotlin into runnable bytecode | ~300 MB |
| **Android SDK Command-line Tools** | Google's downloader for SDK parts (`sdkmanager`) | ~100 MB |
| **SDK Platform + Build-Tools** | Android's libraries + packaging tools (pulled by `sdkmanager`) | ~500 MB |
| **GitHub CLI (`gh`)** | Terminal access to GitHub: auth, releases (APK hosting), repo management | ~50 MB |
| **Gradle** | Build automation — downloaded *automatically* by the wrapper on first build | ~130 MB |

Total disk: ~1 GB in `C:\Users\Jerald\` — nothing installed system-wide, fully removable.

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

Create `C:\Users\Jerald\\.gradle\gradle.properties`:

```properties
sdk.dir=C\:\\Users\\Jerald\\AppData\\Local\\Android\\Sdk
```

(Also set system env var `ANDROID_HOME` to the same path — some tools look for it.)

## Step 5 — Verify everything

```powershell
java -version                                    # 17.x
& $sdkmanager --list_installed                   # 3 packages above
adb version                                      # Android Debug Bridge
```

All three answering = environment ready.

## Maintenance cost of this setup: zero

- The SDK never auto-updates or breaks itself
- Builds work offline once Gradle has cached dependencies
- If the PC dies: this doc rebuilds the environment in ~20 minutes on any Windows machine
