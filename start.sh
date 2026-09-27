#!/usr/bin/env bash
# DTHController launcher script.

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Starting DTHController companion..."

# Check ADB
if command -v adb >/dev/null 2>&1; then
    adb start-server >/dev/null 2>&1
    DEVICE_COUNT=$(adb devices | grep -v "List of devices attached" | grep -c "device$" || true)
    if [ "$DEVICE_COUNT" -gt 0 ]; then
        echo -e "${GREEN}[OK] Tablet detected. Forwarding port 54321...${NC}"
        adb reverse tcp:54321 tcp:54321 >/dev/null 2>&1 || true
    else
        echo -e "${YELLOW}[WARN] No authorized Android device detected over USB.${NC}"
        echo "Check that USB debugging is enabled and the tablet is unlocked."
        adb reverse tcp:54321 tcp:54321 >/dev/null 2>&1 || true
    fi
else
    echo -e "${RED}[ERROR] adb not found. Install android-tools.${NC}"
fi

# Ensure daemon binary exists
if [ ! -f "daemon/rhythm-daemon" ]; then
    echo "rhythm-daemon binary missing. Compiling..."
    make -C daemon
fi

# Terminate existing instance if running
pkill -9 -f rhythm-daemon 2>/dev/null || true
sleep 0.2

echo -e "${GREEN}[OK] Starting rhythm-daemon. Press Ctrl+C to stop.${NC}"
exec ./daemon/rhythm-daemon "$@"
