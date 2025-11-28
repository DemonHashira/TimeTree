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
| `diff [commits]` | Show differences |
| `branch <name>` | Create a branch |
| `branch -l` | List branches |
| `branch -d <name>` | Delete a branch |
| `checkout <branch>` | Switch branches |

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

## License

See [LICENSE](LICENSE) file.


