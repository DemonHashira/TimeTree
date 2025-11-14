# TimeTree

A lightweight version control system built in Kotlin, implementing Git's core concepts from scratch.

## Features

- **Core VCS Commands**: `init`, `add`, `commit`, `status`, `log`, `diff`, `branch`, `checkout`
- **Content-Addressable Storage**: SHA-1 hashing for efficient deduplication
- **Delta Compression**: Rsync-inspired rolling checksum for large files
- **Line-by-Line Diffs**: Myers algorithm for readable change tracking
- **Pure Kotlin**: Zero external dependencies, runs on any JVM

## Quick Start

```bash
# Initialize a repository
timetree init

# Stage files
timetree add file.txt

# Create a commit
timetree commit -m "Initial commit"

# View history
timetree log

# Check status
timetree status
```

## Installation

### Linux and macOS

```bash
./scripts/install.sh
```

Installs to `/usr/local/bin`, `~/.local/bin`, or `~/bin`

### Windows

```powershell
.\scripts\install.ps1
```

Installs to `%LOCALAPPDATA%\TimeTree\bin`

**Note**: If you get an execution policy error:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## Commands

| Command | Description |
|---------|-------------|
| `init` | Initialize a new repository |
| `add <file>` | Stage files for commit |
| `commit -m "msg"` | Create a commit |
| `status` | Show working tree status |
| `log` | Display commit history |
| `diff <commit1> <commit2>` | Show differences |
| `branch <name>` | Create a branch |
| `branch -l` | List branches |
| `branch -d <name>` | Delete a branch |
| `checkout <branch>` | Switch branches |
| `checkout -b <name>` | Create and switch to branch |

**Alias**: Use `tt` instead of `timetree` for shorter commands.

## How It Works

TimeTree stores your project history in a `.timetree/` directory:

- **Objects**: Content-addressable blobs and trees (SHA-1 hashed)
- **Refs**: Branch pointers and HEAD reference
- **Index**: Staging area for commits
- **Commits**: Immutable snapshots with parent links

Each commit creates a snapshot of your entire project. Identical files are stored only once thanks to content addressing.

## Algorithms Implemented

- **SHA-1 Hashing**: Custom implementation for content addressing
- **Myers Diff**: Longest Common Subsequence for line-by-line diffs
- **Rsync Delta**: Rolling checksum for binary delta compression

## Uninstallation

**Linux/macOS:**
```bash
./scripts/uninstall.sh
```

**Windows (PowerShell):**
```powershell
.\scripts\uninstall.ps1
```

**Windows (Command Prompt):**
```cmd
.\scripts\uninstall.bat
```

## Building from Source

```bash
./gradlew shadowJar
```

The JAR will be in `build/libs/timetree.jar`

## Testing

```bash
./gradlew test
```

**Test Coverage**: 75% with comprehensive integration tests

## License

See [LICENSE](LICENSE) file.


