# One-command dev loop: build + install (+ optional launch) on the connected phone.
# Usage:
#   .\deploy.ps1            build + install
#   .\deploy.ps1 -launch    build + install + open the app
param([switch]$launch)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

Write-Host "==> building..." -ForegroundColor Cyan
.\gradlew.bat assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED" -ForegroundColor Red; exit 1 }

Write-Host "==> installing..." -ForegroundColor Cyan
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "INSTALL FAILED - run 'adb devices' (phone connected + authorized?)" -ForegroundColor Red
    exit 1
}

if ($launch) {
    & $adb shell am start -n com.lifenote/.MainActivity
}
Write-Host "DONE - data preserved" -ForegroundColor Green
