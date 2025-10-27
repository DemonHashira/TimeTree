#!/usr/bin/env pwsh
<#
    timetree-current-branch.ps1
    Helper for Starship prompt on PowerShell.
    Starship config on Windows (PowerShell):
        [custom.timetree]
        command = "timetree-current-branch.ps1"
        when = "timetree-current-branch.ps1"
        format = "on [ $output]($style) "
        style = "bold purple"

    Notes:
      - Make sure this script's directory is in $env:PATH.
      - You also need to allow local scripts to execute:
            Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
#>

function Find-TimeTreeRoot {
    param(
        [string] $startDir
    )

    $dir = $startDir
    while ($true) {
        if (Test-Path -Path (Join-Path $dir ".timetree") -PathType Container) {
            return $dir
        }

        $parent = Split-Path $dir -Parent
        if ([string]::IsNullOrEmpty($parent) -or ($parent -eq $dir)) {
            break
        }

        $dir = $parent
    }

    return $null
}

# 1. Find repo root (directory that directly contains .timetree)
$currentDir = (Get-Location).Path
$root = Find-TimeTreeRoot -startDir $currentDir

if (-not $root) {
    exit 1
}

# 2. If we're literally inside "<root>/.timetree", suppress output.
#    That prevents duplicate "on branch on branch" when cd'ing into .timetree.
$internalRepoPath = Join-Path $root ".timetree"
if ($currentDir -eq $internalRepoPath -or
    $currentDir.StartsWith("$internalRepoPath" + [IO.Path]::DirectorySeparatorChar)) {
    exit 1
}

# 3. Read HEAD file
$headFile = Join-Path $internalRepoPath "HEAD"
if (-not (Test-Path -Path $headFile -PathType Leaf)) {
    exit 1
}

$headRef = Get-Content -Raw $headFile

# 4. Attached HEAD case: "ref: refs/heads/test-branch"
if ($headRef -match '^ref:\s+refs/heads/(.+)$') {
    $branchName = $Matches[1]
    # Write just the branch name to stdout
    Write-Output $branchName
    exit 0
}

# 5. Detached HEAD case: HEAD contains raw commit id
if ($headRef.Length -ge 7) {
    $short = $headRef.Substring(0,7)
    Write-Output $short
    exit 0
}

# Fallback: nothing usable
exit 1
