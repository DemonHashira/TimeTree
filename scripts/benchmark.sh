#!/bin/bash
# TimeTree Performance Benchmarks

set -e

# Get absolute path to project directory
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BENCHMARK_DIR="$PROJECT_DIR/benchmark-test"
TIMETREE="$PROJECT_DIR/build/libs/timetree.jar"

# Ensure temp benchmark dir is always cleaned up
cleanup() {
    cd "$PROJECT_DIR" || exit 1
    rm -rf "$BENCHMARK_DIR"
}
trap cleanup EXIT

# Colors
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== TimeTree Performance Benchmarks ===${NC}\n"

declare -a BENCH_DESCRIPTIONS=()
declare -a BENCH_TIMES=()

# Build first
echo "Building TimeTree..."
cd "$PROJECT_DIR"
./gradlew shadowJar --quiet

# Check jar exists
if [ ! -f "$TIMETREE" ]; then
    echo "Error: timetree.jar not found at $TIMETREE"
    exit 1
fi

# Clean up and prepare
rm -rf "$BENCHMARK_DIR"
mkdir -p "$BENCHMARK_DIR"
cd "$BENCHMARK_DIR"

# Helper function to measure time
measure() {
    local description="$1"
    shift
    echo "Testing: $description"
    local start
    start=$(python3 -c 'import time; print(time.time())')
    "$@"
    local end
    end=$(python3 -c 'import time; print(time.time())')
    local duration
    duration=$(python3 -c "print($end - $start)")
    BENCH_DESCRIPTIONS+=("$description")
    BENCH_TIMES+=("$duration")
    printf "✓ Completed in %.3f seconds\n\n" "$duration"
}

# Initialize repository
java -jar "$TIMETREE" init > /dev/null

echo -e "${BLUE}--- Benchmark 1: Staging 100 Files ---${NC}"
# Create 100 small files
for i in {1..100}; do
    echo "Content of file $i - some text to make it realistic" > "file$i.txt"
done

measure "Stage 100 files" java -jar "$TIMETREE" add *.txt > /dev/null

echo -e "${BLUE}--- Benchmark 2: Commit 100 Files ---${NC}"
measure "Commit 100 files" java -jar "$TIMETREE" commit -m "Add 100 files" > /dev/null

echo -e "${BLUE}--- Benchmark 3: Diff Large Text File ---${NC}"
# Create a ~1MB text file with real line breaks so edits register
echo "Creating test file for diff..."
python3 - <<'PY'
with open("diff-test.txt", "w") as f:
    for i in range(1, 10001):
        f.write(
            f"Line {i:04d}: Lorem ipsum dolor sit amet consectetur adipiscing "
            "elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua\n"
        )
PY

java -jar "$TIMETREE" add diff-test.txt > /dev/null
java -jar "$TIMETREE" commit -m "Add diff test file" > /dev/null

# Get the commit ID
commit1=$(java -jar "$TIMETREE" log | grep "^commit" | head -1 | awk '{print $2}')

# Modify multiple lines
for linenum in 100 200 300 400 500; do
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i.bak "${linenum}s/.*/MODIFIED LINE ${linenum} - This line was changed for diff testing/" diff-test.txt
    else
        sed -i "${linenum}s/.*/MODIFIED LINE ${linenum} - This line was changed for diff testing/" diff-test.txt
    fi
done

rm -f diff-test.txt.bak

java -jar "$TIMETREE" add diff-test.txt > /dev/null
java -jar "$TIMETREE" commit -m "Modify diff test file" > /dev/null

# Get the second commit ID
commit2=$(java -jar "$TIMETREE" log | grep "^commit" | head -1 | awk '{print $2}')

measure "Diff text file (5 line changes)" java -jar "$TIMETREE" diff "$commit1" "$commit2" > /dev/null

echo -e "${BLUE}--- Benchmark 4: Checkout 1000 Files ---${NC}"

# Create branch with 1000 files
java -jar "$TIMETREE" branch test-branch > /dev/null
for i in {101..1100}; do
    echo "File $i content" > "checkout-test-$i.txt"
done
java -jar "$TIMETREE" add checkout-test-*.txt > /dev/null
java -jar "$TIMETREE" commit -m "Add 1000 more files" > /dev/null

# Measure checkout back to master
measure "Checkout 1000 files" java -jar "$TIMETREE" checkout master > /dev/null

echo -e "${BLUE}--- Benchmark 5: Create 1000 Commits ---${NC}"
java -jar "$TIMETREE" checkout test-branch > /dev/null
start=$(python3 -c 'import time; print(time.time())')
for i in {1..1000}; do
    echo "Commit $i" > "commit-$i.txt"
    java -jar "$TIMETREE" add "commit-$i.txt" > /dev/null 2>&1
    java -jar "$TIMETREE" commit -m "Commit $i" > /dev/null 2>&1
    if [ $((i % 100)) -eq 0 ]; then
        echo -ne "\rCreating commits... $i/1000"
    fi
done

echo ""
end=$(python3 -c 'import time; print(time.time())')
duration=$(python3 -c "print($end - $start)")
BENCH_DESCRIPTIONS+=("Create 1000 commits")
BENCH_TIMES+=("$duration")
printf "✓ Created 1000 commits in %.3f seconds\n\n" "$duration"

echo -e "${BLUE}--- Benchmark 6: Log 1000 Commits ---${NC}"
measure "Log 1000 commits" java -jar "$TIMETREE" log --all > /dev/null

echo -e "${BLUE}--- Summary ---${NC}"
printf "%-35s %12s\n" "Benchmark" "Time (s)"
printf "%-35s %12s\n" "---------" "--------"

for i in "${!BENCH_DESCRIPTIONS[@]}"; do
    printf "%-35s %12.3f\n" "${BENCH_DESCRIPTIONS[$i]}" "${BENCH_TIMES[$i]}"
done
