$adbPath = "C:\Users\ik807\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$outputPath = Join-Path $PSScriptRoot "player_debug_log.txt"

Write-Host "Clearing existing logcat..."
& $adbPath logcat -c

Write-Host "Starting logcat capture for 60 seconds..."
Write-Host "Filtered to tags: ANIPLEX_JS, ANIPLEX_BRIDGE, chromium, WebView, AndroidRuntime"
Write-Host "Saving output to $outputPath..."

# Start adb logcat as a background process redirecting output to the file
$proc = Start-Process -FilePath $adbPath -ArgumentList "logcat ANIPLEX_JS:D ANIPLEX_BRIDGE:D chromium:D WebView:D AndroidRuntime:D *:S -v time" -NoNewWindow -PassThru -RedirectStandardOutput $outputPath

# Countdown for 60 seconds
for ($i = 60; $i -gt 0; $i--) {
    Write-Progress -Activity "Capturing logs" -Status "$i seconds remaining" -PercentComplete ((60 - $i) * 100 / 60)
    Start-Sleep -Seconds 1
}

# Stop the process
Stop-Process -Id $proc.Id -Force
Write-Host "Logcat capture complete. Logs saved to player_debug_log.txt."
