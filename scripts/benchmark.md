# TimeTree Benchmark Scripts

## Quick Benchmark (30 seconds)

Tests basic performance:
- Add 100 files
- Commit 100 files  
- Status check

### Run:
```bash
./scripts/quick-benchmark.sh

# Windows (PowerShell):
.\scripts\powershell\quick-benchmark.ps1
```

### Expected Output:
```
=== Results ===
Add 100 files:    420ms
Commit 100 files: 365ms
Status:           361ms
```

## Full Benchmark Suite (5-10 minutes)

Tests various performance aspects:
- Stage/commit 100 files
- Diff 10MB file
- Checkout 1000 files
- Create 1000 commits
- Log 1000 commits

### Run:

**Note:** On Linux and macOS, the script must be made executable first:
```bash
chmod +x scripts/benchmark.sh
```

Then run:
```bash
# Linux/macOS:
./scripts/benchmark.sh

# Windows (PowerShell):
.\scripts\powershell\benchmark.ps1
```

## Requirements

- Java 21+
- Python 3 (for timing)
- Gradle (for building)

## Notes

- Scripts automatically build the project
- Tests run in isolated directories
- Cleanup happens automatically
- First run includes JVM warmup overhead
