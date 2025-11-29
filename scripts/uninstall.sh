#!/bin/bash
# TimeTree uninstallation script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Determine installation directory (same logic as install.sh)
if [ -w "/usr/local/bin" ] && [ -f "/usr/local/bin/timetree" ]; then
    INSTALL_DIR="/usr/local/bin"
elif [ -f "$HOME/.local/bin/timetree" ]; then
    INSTALL_DIR="$HOME/.local/bin"
elif [ -f "$HOME/bin/timetree" ]; then
    INSTALL_DIR="$HOME/bin"
else
    echo -e "${RED}Error: Could not find timetree installation${NC}"
    exit 1
fi

echo -e "${YELLOW}Removing TimeTree from $INSTALL_DIR...${NC}"

if [ -f "$INSTALL_DIR/timetree" ]; then
    rm -f "$INSTALL_DIR/timetree"
    echo -e "${GREEN}✓ Removed timetree${NC}"
fi

if [ -f "$INSTALL_DIR/timetree.jar" ]; then
    rm -f "$INSTALL_DIR/timetree.jar"
    echo -e "${GREEN}✓ Removed timetree.jar${NC}"
fi

if [ -f "$INSTALL_DIR/tt" ]; then
    rm -f "$INSTALL_DIR/tt"
    echo -e "${GREEN}✓ Removed tt${NC}"
fi

echo -e "${GREEN}Uninstallation complete!${NC}"
