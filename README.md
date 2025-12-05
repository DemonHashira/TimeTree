# TimeTree

A lightweight version control system built in Kotlin, implementing Git's core concepts from scratch.

## Features

- **Core VCS Commands**: `init`, `add`, `commit`, `status`, `log`, `diff`, `branch`, `checkout`
- **Content-Addressable Storage**: SHA-1 hashing with domain separation for deduplication
- **Delta Compression**: Rsync-style rolling checksum for efficient storage
- **Line-by-Line Diffs**: Myers algorithm for readable change tracking
- **Pure Kotlin**: Written entirely in Kotlin

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

**Prerequisites:** Java JDK 11 or higher (Java 21 recommended). Note: Java 25 does not work due to Gradle compatibility issues. Check with:
```bash
java -version
```

### Linux and macOS

```bash
./scripts/install.sh
```

Installs to `/usr/local/bin`, `~/.local/bin`, or `~/bin`

### Windows

```powershell
.\scripts\powershell\install.ps1
```

Installs to `%LOCALAPPDATA%\TimeTree\bin`

**Note**: If you get an execution policy error:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## Commands

### Core Version Control

| Command | Description |
|---------|-------------|
| `init` | Initialize a new repository |
| `add <file>` | Stage files for commit |
| `commit -m "msg"` | Create a commit |
| `status` | Show working tree status |
| `log` | Display commit history |
| `diff [commits]` | Show differences |
| `branch <name>` | Create a branch |
| `branch -l` | List branches |
| `branch -d <name>` | Delete a branch |
| `checkout <branch>` | Switch branches |

### Low-Level Commands

| Command | Description |
|---------|-------------|
| `hash-object <path>` | Compute object hash for a file |
| `sig <basis> [-o output] [-b block-size]` | Generate signature for delta compression |
| `delta <signature> <target> [-o output]` | Create delta from signature and target |
| `patch <basis> <delta> [-o output]` | Apply delta to reconstruct file |

**Alias**: Use `tt` instead of `timetree` for shorter commands.

## Architecture

TimeTree stores project history in a `.timetree/` directory:

- **Objects**: Content-addressable blobs, trees, and commits
- **Refs**: Branch pointers and HEAD reference
- **Index**: Staging area for commits
- **Delta Store**: Compressed storage for large files

### Algorithms Implemented

- **HashAlgorithm**: SHA-1 implementation with namespace separation
- **DiffAlgorithm**: Myers O(ND) algorithm for line diffs
- **DeltaAlgorithm**: Rsync-style rolling checksum for binary compression

## Uninstallation

**Linux/macOS:**
```bash
./scripts/uninstall.sh
```

**Windows (PowerShell):**
```powershell
.\scripts\powershell\uninstall.ps1
```

The JAR will be in `build/libs/timetree.jar`

## Testing

```bash
./gradlew test
```

## License

See [LICENSE](LICENSE) file.

