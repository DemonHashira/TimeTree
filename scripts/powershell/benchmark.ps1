# TimeTree Performance Benchmark Script
[CmdletBinding()]
param ()

$ErrorActionPreference = "Stop"

$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$BenchmarkDir = Join-Path $ProjectDir "benchmark-test"
$TimeTree = Join-Path $ProjectDir "build\libs\timetree.jar"

$BenchDescriptions = New-Object System.Collections.Generic.List[string]
$BenchTimes = New-Object System.Collections.Generic.List[double]

function Measure-Bench {
    param(
        [string] $Description,
        [scriptblock] $Action
    )

    Write-Host "Testing: $Description"

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $Action
    $sw.Stop()

    $seconds = $sw.Elapsed.TotalSeconds
    $BenchDescriptions.Add($Description) | Out-Null
    $BenchTimes.Add($seconds) | Out-Null

    $msg = "Completed in {0:N3} seconds" -f $seconds
    Write-Host $msg
    Write-Host ""
}

function Get-LatestCommit {
    param(
        [string] $LogText
    )

    $match =
    Select-String -InputObject $LogText -Pattern '^commit\s+([0-9a-f]{40})' -AllMatches |
        Select-Object -First 1

    if ($null -eq $match) {
        throw "Unable to parse commit id from log output."
    }

    return $match.Matches[0].Groups[1].Value
}

Write-Host "=== TimeTree Performance Benchmarks ==="
Write-Host ""

# Build first
Push-Location $ProjectDir
& ./gradlew shadowJar --quiet
Pop-Location

if (-not (Test-Path -Path $TimeTree -PathType Leaf)) {
    throw "Error: timetree.jar not found at $TimeTree"
}

# Prepare workspace
if (Test-Path $BenchmarkDir) {
    Remove-Item $BenchmarkDir -Recurse -Force
}
New-Item -ItemType Directory -Path $BenchmarkDir | Out-Null
Push-Location $BenchmarkDir

try {
    # Initialize repository
    & java -jar $TimeTree init > $null
    if ($LASTEXITCODE -ne 0) {
        throw "TimeTree init failed with exit code $LASTEXITCODE"
    }

    Write-Host "--- Benchmark 1: Staging 100 Files ---"

    for ($i = 1; $i -le 100; $i++) {
        "Content of file $i - some text to make it realistic" |
            Set-Content -Path ("file{0}.txt" -f $i)
    }

    Measure-Bench "Stage 100 files" {
        & java -jar $TimeTree add *.txt > $null
    }

    Write-Host "--- Benchmark 2: Commit 100 Files ---"

    Measure-Bench "Commit 100 files" {
        & java -jar $TimeTree commit -m "Add 100 files" > $null
    }

    Write-Host "--- Benchmark 3: Diff Large Text File ---"
    Write-Host "Creating test file for diff..."

    $diffLines =
    for ($i = 1; $i -le 1000; $i++) {
        "Line {0:D4}: Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua" -f $i
    }

    Set-Content -Path "diff-test.txt" -Value $diffLines

    & java -jar $TimeTree add diff-test.txt > $null
    & java -jar $TimeTree commit -m "Add diff test file" > $null

    $commit1 = Get-LatestCommit -LogText ( & java -jar $TimeTree log )

    $updatedLines = Get-Content -Path "diff-test.txt"

    foreach ($lineNum in 100, 200, 300, 400, 500) {
        $index = $lineNum - 1
        if ($index -lt $updatedLines.Count) {
            $updatedLines[$index] = "MODIFIED LINE $lineNum - This line was changed for diff testing"
        }
    }

    Set-Content -Path "diff-test.txt" -Value $updatedLines

    & java -jar $TimeTree add diff-test.txt > $null
    & java -jar $TimeTree commit -m "Modify diff test file" > $null

    $commit2 = Get-LatestCommit -LogText ( & java -jar $TimeTree log )

    Measure-Bench "Diff text file (5 line changes)" {
        & java -jar $TimeTree diff $commit1 $commit2 > $null
    }

    Write-Host "--- Benchmark 4: Checkout 1000 Files ---"

    & java -jar $TimeTree branch test-branch > $null

    for ($i = 101; $i -le 1100; $i++) {
        "File $i content" |
            Set-Content -Path ("checkout-test-{0}.txt" -f $i)
    }

    & java -jar $TimeTree add checkout-test-*.txt > $null
    & java -jar $TimeTree commit -m "Add 1000 more files" > $null

    Measure-Bench "Checkout 1000 files" {
        & java -jar $TimeTree checkout master > $null
    }

    Write-Host "--- Benchmark 5: Create 200 Commits ---"

    & java -jar $TimeTree checkout test-branch > $null

    $commitCount = 200
    $swCommits = [System.Diagnostics.Stopwatch]::StartNew()

    for ($i = 1; $i -le $commitCount; $i++) {
        "Commit $i" |
            Set-Content -Path ("commit-{0}.txt" -f $i)

        & java -jar $TimeTree add ("commit-{0}.txt" -f $i) > $null 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "TimeTree add failed at commit $i with exit code $LASTEXITCODE"
        }

        & java -jar $TimeTree commit -m ("Commit {0}" -f $i) > $null 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "TimeTree commit failed at commit $i with exit code $LASTEXITCODE"
        }

        if ($i % 50 -eq 0) {
            $progress = "Creating commits... {0}/{1}" -f $i, $commitCount
            Write-Host -NoNewline "`r$progress"
        }
    }

    Write-Host ""
    $swCommits.Stop()

    $commitSeconds = $swCommits.Elapsed.TotalSeconds
    $BenchDescriptions.Add("Create 200 commits") | Out-Null
    $BenchTimes.Add($commitSeconds) | Out-Null

    $commitMsg = "Created 200 commits in {0:N3} seconds" -f $commitSeconds
    Write-Host $commitMsg
    Write-Host ""

    Write-Host "--- Benchmark 6: Log commits ---"

    Measure-Bench "Log commits" {
        & java -jar $TimeTree log --all > $null
    }

    Write-Host "--- Summary ---"
    Write-Host ("{0,-35} {1,12}" -f "Benchmark", "Time (s)")
    Write-Host ("{0,-35} {1,12}" -f "---------", "--------")

    for ($i = 0; $i -lt $BenchDescriptions.Count; $i++) {
        $line = "{0,-35} {1,12:N3}" -f $BenchDescriptions[$i], $BenchTimes[$i]
        Write-Host $line
    }

    Write-Host ""
}
finally {
    Pop-Location

    if (Test-Path $BenchmarkDir) {
        Remove-Item $BenchmarkDir -Recurse -Force
    }
}
