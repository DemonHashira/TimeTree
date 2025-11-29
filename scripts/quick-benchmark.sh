#!/bin/bash
# Quick Performance Test for TimeTree

set -e

# Get absolute path to project directory
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TIMETREE="$PROJECT_DIR/build/libs/timetree.jar"
TEST_DIR="$PROJECT_DIR/perf-test"

echo "=== Quick TimeTree Performance Test ==="
echo ""

# Build first
echo "Building TimeTree..."
cd "$PROJECT_DIR"
./gradlew shadowJar --quiet

# Check jar exists
if [ ! -f "$TIMETREE" ]; then
    echo "Error: timetree.jar not found"
    exit 1
fi

# Cleanup
rm -rf "$TEST_DIR"
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

# Initialize
java -jar "$TIMETREE" init > /dev/null

echo "1. Testing: Add 100 files..."
for i in {1..100}; do
    echo "File $i content" > "file$i.txt"
done

start=$(python3 -c 'import time; print(int(time.time() * 1000))')
java -jar "$TIMETREE" add *.txt > /dev/null
end=$(python3 -c 'import time; print(int(time.time() * 1000))')
time1=$((end - start))
echo "Completed in ${time1}ms"

echo "2. Testing: Commit 100 files..."
start=$(python3 -c 'import time; print(int(time.time() * 1000))')
java -jar "$TIMETREE" commit -m "Add 100 files" > /dev/null
end=$(python3 -c 'import time; print(int(time.time() * 1000))')
time2=$((end - start))
echo "Completed in ${time2}ms"

echo "3. Testing: Status with 100 files..."
start=$(python3 -c 'import time; print(int(time.time() * 1000))')
java -jar "$TIMETREE" status > /dev/null
end=$(python3 -c 'import time; print(int(time.time() * 1000))')
time3=$((end - start))
echo "Completed in ${time3}ms"

echo ""
echo "=== Results ==="
echo "Add 100 files:    ${time1}ms"
echo "Commit 100 files: ${time2}ms"
echo "Status:           ${time3}ms"
echo ""

cd "$PROJECT_DIR"
rm -rf "$TEST_DIR"
