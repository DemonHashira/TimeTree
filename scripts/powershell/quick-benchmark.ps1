# TimeTree Quick Performance Benchmark Script
[CmdletBinding()]
param ()

$ErrorActionPreference = "Stop"

$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$TimeTree = Join-Path $ProjectDir "build\libs\timetree.jar"
$TestDir = Join-Path $ProjectDir "perf-test"

function Measure-MS {
    param(
        [string] $Description,
        [scriptblock] $Action
    )

    Write-Host $Description
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $Action
    $sw.Stop()
    $elapsed = $sw.ElapsedMilliseconds
    Write-Host "   `u2713 Completed in ${elapsed}ms"
    return $elapsed
}

Write-Host "=== Quick TimeTree Performance Test ===`n"

# Build first
Push-Location $ProjectDir
& ./gradlew shadowJar --quiet
Pop-Location

if (-not (Test-Path -Path $TimeTree -PathType Leaf)) {
    throw "Error: timetree.jar not found"
}

# Cleanup
if (Test-Path $TestDir) {
    Remove-Item $TestDir -Recurse -Force
}
New-Item -ItemType Directory -Path $TestDir | Out-Null
Push-Location $TestDir

# Initialize
& java -jar $TimeTree init > $null

Write-Host "1. Testing: Add 100 files..."
for ($i = 1; $i -le 100; $i++) {
    "File $i content" | Set-Content -Path ("file{0}.txt" -f $i)
}
$time1 = Measure-MS "Adding files" { & java -jar $TimeTree add *.txt > $null }

Write-Host "2. Testing: Commit 100 files..."
$time2 = Measure-MS "Committing files" { & java -jar $TimeTree commit -m "Add 100 files" > $null }

Write-Host "3. Testing: Status with 100 files..."
$time3 = Measure-MS "Status" { & java -jar $TimeTree status > $null }

Write-Host "`n=== Results ==="
Write-Host ("Add 100 files:    {0}ms" -f $time1)
Write-Host ("Commit 100 files: {0}ms" -f $time2)
Write-Host ("Status:           {0}ms" -f $time3)
Write-Host ""

Pop-Location
Remove-Item $TestDir -Recurse -Force
