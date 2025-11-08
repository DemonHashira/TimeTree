# TimeTree uninstallation script for Windows PowerShell

$ErrorActionPreference = "Stop"

$InstallDir = Join-Path $env:LOCALAPPDATA "TimeTree\bin"

if (-not (Test-Path (Join-Path $InstallDir "timetree.bat"))) {
    Write-Host "Error: Could not find timetree installation" -ForegroundColor Red
    exit 1
}

Write-Host "Removing TimeTree from $InstallDir..." -ForegroundColor Yellow

if (Test-Path (Join-Path $InstallDir "timetree.bat")) {
    Remove-Item (Join-Path $InstallDir "timetree.bat") -Force
    Write-Host "✓ Removed timetree.bat" -ForegroundColor Green
}

if (Test-Path (Join-Path $InstallDir "timetree.jar")) {
    Remove-Item (Join-Path $InstallDir "timetree.jar") -Force
    Write-Host "✓ Removed timetree.jar" -ForegroundColor Green
}

if (Test-Path (Join-Path $InstallDir "tt.bat")) {
    Remove-Item (Join-Path $InstallDir "tt.bat") -Force
    Write-Host "✓ Removed tt.bat" -ForegroundColor Green
}

# Remove directory if empty
if (Test-Path $InstallDir) {
    try {
        Remove-Item $InstallDir -Force -ErrorAction SilentlyContinue
        Write-Host "✓ Removed installation directory" -ForegroundColor Green
    } catch {
        # Directory not empty or other error, ignore
    }
}

Write-Host "Uninstallation complete!" -ForegroundColor Green
