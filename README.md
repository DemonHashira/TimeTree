# TimeTree

TimeTree is a Kotlin CLI application that version-controls any directory by storing content-addressable blob and tree objects in a hidden .timetree/ folder. It supports init, add, commit, log, diff (Myers LCS), checkout, branch management (branch, checkout <branch>, branch -l), status for inspecting uncommitted changes, and optional rolling-checksum delta storage for large files. Under the hood, each commit creates an immutable snapshot graph via SHA-1–style hashes—identical blobs and subtrees are stored only once—while an index-based staging area enables selective tracking. Its modular Kotlin design leverages Clikt for concise command definitions, coroutines for parallel hashing and delta computations, and clean separation of object storage, diff engine, and CLI processing into cohesive packages. Built in pure Kotlin on the JVM with zero external dependencies, TimeTree scales to thousands of files with near-interactive performance. Core algorithms implemented from scratch include a
custom SHA-1–style hash for content addressing, the Myers Longest Common Subsequence algorithm for line-by-line diffs, and an rsync-inspired rolling-checksum method for binary delta generation and application.

## Installation

### Linux and macOS

To install TimeTree system-wide (so you can use `timetree` and `tt` commands from anywhere):

```bash
./scripts/install.sh
```

### Windows

Run the PowerShell installation script:

```powershell
.\scripts\install.ps1
```

**Note**: If you get an execution policy error, run:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```
Then try the install script again.

The installation scripts will:
1. Build the TimeTree shadow JAR
2. Create the `tt` wrapper script
3. Install both `timetree` and `tt` commands to a directory in your PATH

**Linux/macOS**: Installs to `/usr/local/bin`, `~/.local/bin`, or `~/bin`  
**Windows**: Installs to `%LOCALAPPDATA%\TimeTree\bin`

If the installation directory is not in your PATH, the script will tell you how to add it.

**Note for Windows users**: After installation, you may need to restart your terminal for PATH changes to take effect.

## Uninstallation

### Linux and macOS

To remove TimeTree:

```bash
./scripts/uninstall.sh
```

### Windows

**PowerShell:**
```powershell
.\scripts\uninstall.ps1
```

**Command Prompt:**
```cmd
.\scripts\uninstall.bat
```

This will remove the `timetree`, `timetree.jar`, and `tt` files from the installation directory.

