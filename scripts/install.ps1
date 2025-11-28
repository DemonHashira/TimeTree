# TimeTree installation script for Windows PowerShell
# This script builds and installs timetree and tt commands

$ErrorActionPreference = "Stop"

# Get script directory and project root
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

# Change to project root
Set-Location $ProjectRoot

Write-Host "Building TimeTree..." -ForegroundColor Green
& .\gradlew.bat shadowJar --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to build TimeTree" -ForegroundColor Red
    exit 1
}

$JarFile = Join-Path $ProjectRoot "build\libs\timetree.jar"

# Check if JAR exists
if (-not (Test-Path $JarFile)) {
    Write-Host "Error: timetree.jar not found at $JarFile" -ForegroundColor Red
    exit 1
}

# Determine installation directory
$InstallDir = Join-Path $env:LOCALAPPDATA "TimeTree\bin"
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}

Write-Host "Installing timetree to $InstallDir..." -ForegroundColor Green

# Create wrapper batch file for timetree
$TimetreeBat = @"
@echo off
REM TimeTree wrapper script
set "JAR_FILE=%~dp0timetree.jar"
java -jar "%JAR_FILE%" %*
"@
$TimetreeBat | Out-File -FilePath (Join-Path $InstallDir "timetree.bat") -Encoding ASCII

# Copy the JAR file
Copy-Item $JarFile (Join-Path $InstallDir "timetree.jar") -Force

# Create tt.bat wrapper
$TtBat = @"
@echo off
REM TimeTree 'tt' wrapper script
set "JAR_FILE=%~dp0timetree.jar"
java -jar "%JAR_FILE%" %*
"@
$TtBat | Out-File -FilePath (Join-Path $InstallDir "tt.bat") -Encoding ASCII

Write-Host "✓ Successfully installed timetree and tt to $InstallDir" -ForegroundColor Green

# Check if directory is in PATH
$CurrentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($CurrentPath -notlike "*$InstallDir*") {
    Write-Host ""
    Write-Host "Warning: $InstallDir is not in your PATH" -ForegroundColor Yellow
    Write-Host "Add this directory to your PATH:" -ForegroundColor Yellow
    Write-Host "  1. Press Win+R, type 'sysdm.cpl' and press Enter" -ForegroundColor Yellow
    Write-Host "  2. Go to 'Advanced' tab, click 'Environment Variables'" -ForegroundColor Yellow
    Write-Host "  3. Under 'User variables', select 'Path' and click 'Edit'" -ForegroundColor Yellow
    Write-Host "  4. Click 'New' and add: $InstallDir" -ForegroundColor Yellow
    Write-Host "  5. Click OK on all dialogs" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Or add to PATH with this PowerShell command:" -ForegroundColor Yellow

    $PathCommand = "[Environment]::SetEnvironmentVariable('Path', [Environment]::GetEnvironmentVariable('Path','User') + ';$InstallDir', 'User')"
    Write-Host "  $PathCommand" -ForegroundColor Green
} else {
    Write-Host "+ $InstallDir is already in your PATH" -ForegroundColor Green
}

Write-Host ""
Write-Host "Installation complete! You can now use 'timetree' and 'tt' commands." -ForegroundColor Green
Write-Host "Note: You may need to restart your terminal for PATH changes to take effect." -ForegroundColor Yellow
