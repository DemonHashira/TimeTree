#!/bin/bash
# TimeTree installation script
# This script builds and installs timetree and tt commands

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Get the project root directory (parent of scripts directory)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Change to project root directory
cd "$PROJECT_ROOT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Determine installation directory
if [ -w "/usr/local/bin" ]; then
    INSTALL_DIR="/usr/local/bin"
elif [ -w "$HOME/.local/bin" ]; then
    INSTALL_DIR="$HOME/.local/bin"
    mkdir -p "$INSTALL_DIR"
elif [ -w "$HOME/bin" ]; then
    INSTALL_DIR="$HOME/bin"
    mkdir -p "$INSTALL_DIR"
else
    INSTALL_DIR="$HOME/.local/bin"
    mkdir -p "$INSTALL_DIR"
    echo -e "${YELLOW}Warning: Installing to $INSTALL_DIR${NC}"
    echo -e "${YELLOW}Make sure $INSTALL_DIR is in your PATH${NC}"
fi

echo -e "${GREEN}Building TimeTree...${NC}"
./gradlew shadowJar --no-daemon

JAR_FILE="$PROJECT_ROOT/build/libs/timetree.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: timetree.jar not found at $JAR_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}Installing timetree to $INSTALL_DIR...${NC}"

# Create wrapper script for timetree that runs the JAR
cat > "$INSTALL_DIR/timetree" << 'EOF'
#!/bin/sh
# TimeTree wrapper script
JAR_FILE="$(dirname "$0")/timetree.jar"
exec java -jar "$JAR_FILE" "$@"
EOF

# Copy the JAR file
cp "$JAR_FILE" "$INSTALL_DIR/timetree.jar"

# Create tt as a copy of timetree wrapper
cat > "$INSTALL_DIR/tt" << 'EOF'
#!/bin/sh
# TimeTree 'tt' wrapper script
JAR_FILE="$(dirname "$0")/timetree.jar"
exec java -jar "$JAR_FILE" "$@"
EOF

# Make scripts executable
chmod +x "$INSTALL_DIR/timetree"
chmod +x "$INSTALL_DIR/tt"

echo -e "${GREEN}✓ Successfully installed timetree and tt to $INSTALL_DIR${NC}"

# Check if directory is in PATH
if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]]; then
    echo -e "${YELLOW}Warning: $INSTALL_DIR is not in your PATH${NC}"
    echo -e "${YELLOW}Add this line to your ~/.zshrc (or ~/.bashrc):${NC}"
    echo -e "${GREEN}export PATH=\"\$PATH:$INSTALL_DIR\"${NC}"
    echo ""
    echo -e "${YELLOW}Then run: source ~/.zshrc${NC}"
else
    echo -e "${GREEN}✓ $INSTALL_DIR is already in your PATH${NC}"
fi

echo ""
echo -e "${GREEN}Installation complete! You can now use 'timetree' and 'tt' commands.${NC}"
