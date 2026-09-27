#!/usr/bin/env bash
# DTHController (Digital Twin : Harmonix) automated setup script.

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== DTHController Setup ==="

# 1. Dependency checks
echo "[1/4] Checking dependencies..."
MISSING_PKGS=()
command -v gcc >/dev/null 2>&1 || MISSING_PKGS+=("gcc")
command -v make >/dev/null 2>&1 || MISSING_PKGS+=("make")
command -v adb >/dev/null 2>&1 || MISSING_PKGS+=("adb")

if [ ${#MISSING_PKGS[@]} -ne 0 ]; then
    echo -e "${YELLOW}Missing packages: ${MISSING_PKGS[*]}${NC}"
    echo "Installing missing packages..."
    if command -v pacman >/dev/null 2>&1; then
        sudo pacman -S --needed base-devel android-tools
    elif command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update && sudo apt-get install -y build-essential adb
    elif command -v dnf >/dev/null 2>&1; then
        sudo dnf install -y gcc make android-tools
    elif command -v zypper >/dev/null 2>&1; then
        sudo zypper install -y gcc make android-tools
    else
        echo -e "${RED}Error: Package manager not recognized. Please install: ${MISSING_PKGS[*]}${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}[OK] Required build tools found.${NC}"
fi

# 2. Kernel module & /dev/uinput setup
echo "[2/4] Checking /dev/uinput permissions..."
if ! lsmod | grep -q "^uinput"; then
    echo "Loading uinput module..."
    sudo modprobe uinput
fi

if [ ! -f /etc/modules-load.d/uinput.conf ]; then
    echo "Configuring automatic uinput loading on boot..."
    echo "uinput" | sudo tee /etc/modules-load.d/uinput.conf >/dev/null
fi

UDEV_RULE_PATH="/etc/udev/rules.d/99-uinput.rules"
DESIRED_RULE='KERNEL=="uinput", MODE="0660", GROUP="input", TAG+="uaccess"'

if [ ! -f "$UDEV_RULE_PATH" ] || ! grep -q 'KERNEL=="uinput"' "$UDEV_RULE_PATH"; then
    echo "Installing udev rule to allow non-root uinput access..."
    echo "$DESIRED_RULE" | sudo tee "$UDEV_RULE_PATH" >/dev/null
    sudo udevadm control --reload-rules
    sudo udevadm trigger
fi

if ! groups "$USER" | grep -q '\binput\b'; then
    echo "Adding $USER to input group..."
    sudo usermod -aG input "$USER"
    echo -e "${YELLOW}Note: Added $USER to group 'input'. You may need to log out and back in to apply this change.${NC}"
else
    echo -e "${GREEN}[OK] User $USER has input group membership.${NC}"
fi

# 3. Build native Linux daemon
echo "[3/4] Compiling rhythm-daemon..."
make -C daemon clean
make -C daemon

if [ -f "daemon/rhythm-daemon" ]; then
    echo -e "${GREEN}[OK] rhythm-daemon compiled.${NC}"
else
    echo -e "${RED}[ERROR] Failed to compile rhythm-daemon.${NC}"
    exit 1
fi

# 4. Check connected Android device
echo "[4/4] Checking connected Android devices via ADB..."
adb start-server >/dev/null 2>&1
DEVICES=$(adb devices | grep -v "List of devices attached" | grep -v "^$" || true)

if [ -z "$DEVICES" ]; then
    echo -e "${YELLOW}[WARN] No Android device detected over USB.${NC}"
    echo "Ensure your tablet has USB debugging enabled and is plugged in."
else
    echo -e "${GREEN}[OK] Connected device found:${NC}"
    echo "$DEVICES"
    
    if [ -f "rhythm-controller.apk" ]; then
        read -r -p "Install rhythm-controller.apk on tablet now? [Y/n] " response
        response=${response:-Y}
        if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            echo "Installing rhythm-controller.apk..."
            adb install -r rhythm-controller.apk
            echo -e "${GREEN}[OK] APK installed.${NC}"
        fi
    fi
fi

chmod +x start.sh 2>/dev/null || true

echo ""
echo "=== Setup complete ==="
echo "To start the controller:"
echo "  1. Connect your Android tablet via USB."
echo "  2. Launch DTHController on the tablet."
echo "  3. Run: ./start.sh"
