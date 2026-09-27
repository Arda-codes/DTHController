#!/usr/bin/env bash
# ==============================================================================
# ⚡ DTHController (Digital Twin : Harmonix) - Automated Setup Script
# Works on Arch/CachyOS, Ubuntu/Debian, Fedora, openSUSE, etc.
# ==============================================================================

set -e

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${CYAN}${BOLD}"
echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║          ⚡ DTHCONTROLLER (DIGITAL TWIN : HARMONIX) ⚡           ║"
echo "║      Ultra-Low-Latency Android Tablet Controller for Linux        ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# 1. Dependency Detection & Installation
echo -e "${CYAN}[1/5] Checking host dependencies...${NC}"

MISSING_PKGS=()
command -v gcc >/dev/null 2>&1 || MISSING_PKGS+=("gcc")
command -v make >/dev/null 2>&1 || MISSING_PKGS+=("make")
command -v adb >/dev/null 2>&1 || MISSING_PKGS+=("adb")

if [ ${#MISSING_PKGS[@]} -ne 0 ]; then
    echo -e "${YELLOW}Missing packages detected: ${MISSING_PKGS[*]}${NC}"
    echo -e "Attempting to install missing packages..."
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
    echo -e "${GREEN}✓ All required dependencies (gcc, make, adb) are installed.${NC}"
fi

# 2. Kernel Module & /dev/uinput Setup
echo -e "\n${CYAN}[2/5] Configuring /dev/uinput permissions...${NC}"

# Ensure uinput module loads
if ! lsmod | grep -q "^uinput"; then
    echo "Loading uinput kernel module..."
    sudo modprobe uinput
fi

if [ ! -f /etc/modules-load.d/uinput.conf ]; then
    echo "Configuring automatic uinput loading on boot..."
    echo "uinput" | sudo tee /etc/modules-load.d/uinput.conf >/dev/null
fi

# Set up udev rule
UDEV_RULE_PATH="/etc/udev/rules.d/99-uinput.rules"
DESIRED_RULE='KERNEL=="uinput", MODE="0660", GROUP="input", TAG+="uaccess"'

NEEDS_UDEV_UPDATE=0
if [ ! -f "$UDEV_RULE_PATH" ] || ! grep -q 'KERNEL=="uinput"' "$UDEV_RULE_PATH"; then
    NEEDS_UDEV_UPDATE=1
fi

if [ $NEEDS_UDEV_UPDATE -eq 1 ]; then
    echo "Installing udev rule for non-root /dev/uinput access..."
    echo "$DESIRED_RULE" | sudo tee "$UDEV_RULE_PATH" >/dev/null
    sudo udevadm control --reload-rules
    sudo udevadm trigger
    echo -e "${GREEN}✓ udev rules installed at $UDEV_RULE_PATH${NC}"
else
    echo -e "${GREEN}✓ udev rules for /dev/uinput already configured.${NC}"
fi

# Ensure user is in input group
if ! groups "$USER" | grep -q '\binput\b'; then
    echo "Adding $USER to 'input' group..."
    sudo usermod -aG input "$USER"
    echo -e "${YELLOW}Note: Added $USER to group 'input'. You may need to log out and back in for this group to take full effect.${NC}"
else
    echo -e "${GREEN}✓ User $USER is in 'input' group.${NC}"
fi

# 3. Build Native Linux Daemon
echo -e "\n${CYAN}[3/5] Compiling Linux companion daemon (rhythm-daemon)...${NC}"
make -C daemon clean
make -C daemon

if [ -f "daemon/rhythm-daemon" ]; then
    echo -e "${GREEN}✓ Compiled rhythm-daemon successfully with -O3 optimization.${NC}"
else
    echo -e "${RED}Error: Failed to compile rhythm-daemon.${NC}"
    exit 1
fi

# 4. Check ADB & Android Tablet
echo -e "\n${CYAN}[4/5] Checking connected Android devices via ADB...${NC}"
adb start-server >/dev/null 2>&1

DEVICES=$(adb devices | grep -v "List of devices attached" | grep -v "^$" || true)

if [ -z "$DEVICES" ]; then
    echo -e "${YELLOW}⚠️  No Android device detected over USB.${NC}"
    echo "Please ensure:"
    echo "  1. Your tablet is connected via USB."
    echo "  2. USB Debugging is turned ON in Developer Options."
    echo "  3. You accepted the 'Allow USB debugging' prompt on the tablet screen."
else
    echo -e "${GREEN}✓ Detected connected Android device(s):${NC}"
    echo "$DEVICES"
    
    if [ -f "rhythm-controller.apk" ]; then
        read -r -p "Install / Update rhythm-controller.apk on tablet now? [Y/n] " response
        response=${response:-Y}
        if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            echo "Installing rhythm-controller.apk..."
            adb install -r rhythm-controller.apk
            echo -e "${GREEN}✓ APK installed successfully!${NC}"
        fi
    fi
fi

# 5. Create / Verify Run Script
echo -e "\n${CYAN}[5/5] Finalizing startup launcher...${NC}"
chmod +x start.sh 2>/dev/null || true

echo -e "\n${GREEN}${BOLD}═══════════════════════════════════════════════════════════════════"
echo -e "                 🎉 SETUP COMPLETED SUCCESSFULLY!                 "
echo -e "═══════════════════════════════════════════════════════════════════${NC}"
echo -e "To start playing:"
echo -e "  1. Connect your Android tablet via USB."
echo -e "  2. Open the ${CYAN}Rhythm Controller${NC} app on your tablet."
echo -e "  3. Run: ${GREEN}./start.sh${NC}"
echo -e "     (This automatically forwards port 54321 and launches the daemon)"
echo -e "═══════════════════════════════════════════════════════════════════\n"
