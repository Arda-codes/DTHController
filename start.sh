#!/usr/bin/env bash
# ==============================================================================
# ⚡ DTHController (Digital Twin : Harmonix) - Quick Play Launcher
# Automatically checks ADB reverse and starts the Linux companion daemon.
# ==============================================================================

set -e

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${CYAN}${BOLD}⚡ Starting DTHController Companion (Digital Twin : Harmonix)...${NC}"

# Check ADB
if command -v adb >/dev/null 2>&1; then
    adb start-server >/dev/null 2>&1
    
    # Check if a device is connected
    DEVICE_COUNT=$(adb devices | grep -v "List of devices attached" | grep -c "device$" || true)
    if [ "$DEVICE_COUNT" -gt 0 ]; then
        echo -e "${GREEN}✓ Android tablet detected. Setting up port tunnel (54321)...${NC}"
        adb reverse tcp:54321 tcp:54321 >/dev/null 2>&1 || true
    else
        echo -e "${YELLOW}⚠️  No authorized Android device detected over USB.${NC}"
        echo -e "${YELLOW}   Please ensure USB debugging is enabled and the tablet is unlocked.${NC}"
        echo -e "${YELLOW}   Attempting adb reverse anyway...${NC}"
        adb reverse tcp:54321 tcp:54321 >/dev/null 2>&1 || true
    fi
else
    echo -e "${RED}Warning: 'adb' not found in PATH. Make sure android-tools is installed.${NC}"
fi

# Check daemon binary
if [ ! -f "daemon/rhythm-daemon" ]; then
    echo -e "${YELLOW}Daemon binary not found. Compiling now...${NC}"
    make -C daemon
fi

# Cleanup old running instances
pkill -9 -f rhythm-daemon 2>/dev/null || true
sleep 0.2

echo -e "${GREEN}${BOLD}✓ Launching rhythm-daemon with real-time priority!${NC}"
echo -e "${CYAN}Press Ctrl+C at any time to exit.${NC}\n"

exec ./daemon/rhythm-daemon "$@"
