# Show current TimeTree branch for Starship prompt
<#
    timetree-current-branch.ps1
    Helper for Starship prompt on PowerShell.
    Starship config on Windows (PowerShell):
        [custom.timetree]
        command = "timetree-current-branch.ps1"
        when = "timetree-current-branch.ps1"
        format = "on [ $output]($style) "
        style = "bold purple"

        [directory]
        truncation_length = 1

        truncate_to_repo = false
        truncation_symbol = ""
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

# Find repo root
$currentDir = (Get-Location).Path
$root = Find-TimeTreeRoot -startDir $currentDir

if (-not $root) {
    exit 1
}

# If we're literally inside "<root>/.timetree", suppress output.
# That prevents duplicate "on branch on branch" when cd'ing into .timetree.
$internalRepoPath = Join-Path $root ".timetree"
if ($currentDir -eq $internalRepoPath -or
    $currentDir.StartsWith("$internalRepoPath" + [IO.Path]::DirectorySeparatorChar)) {
    exit 1
}

# Read HEAD file
$headFile = Join-Path $internalRepoPath "HEAD"
if (-not (Test-Path -Path $headFile -PathType Leaf)) {
    exit 1
}

$headRef = Get-Content -Raw $headFile

if ($headRef -match '^ref:\s+refs/heads/(.+)$') {
    $branchName = $Matches[1]
    Write-Output $branchName
    exit 0
}

if ($headRef.Length -ge 7) {
    $short = $headRef.Substring(0, 7)
    Write-Output $short
    exit 0
}

exit 1
