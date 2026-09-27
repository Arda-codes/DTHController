# ⚡ DTHController (Digital Twin : Harmonix)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Platform: Linux](https://img.shields.io/badge/Platform-Linux%20(X11%20%2F%20Wayland)-orange.svg)](https://kernel.org)
[![Android: 8.0+](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Latency: Sub--Millisecond](https://img.shields.io/badge/Latency-Sub--Millisecond-brightgreen.svg)]()

**DTHController (Digital Twin : Harmonix)** is an ultra-low-latency, zero-overhead rhythm game controller system that turns an **Android tablet** (via USB) into a high-precision input device for **Linux**. Ideal for games like **osu!mania**, **Clone Hero**, **Etterna**, **Project Sekai / Sonolus**, **Project Diva**, and **SDVX / K-Shoot MANIA**.

---

## 📑 Table of Contents
- [Architecture & Data Flow](#-architecture--data-flow)
- [Key Features](#-key-features)
- [Ultra-Low-Latency Engineering](#-ultra-low-latency-engineering)
- [Quick Start (Automated Setup)](#-quick-start-automated-setup)
- [Manual Setup & Compilation](#-manual-setup--compilation)
- [In-App Settings & Customization](#-in-app-settings--customization)
- [Wire Protocol Specification](#-wire-protocol-specification)
- [Tablet Latency Optimization Guide](#-tablet-latency-optimization-guide)
- [Troubleshooting & FAQ](#-troubleshooting--faq)
- [License](#-license)

---

## 🏛️ Architecture & Data Flow

```
   ┌─────────────────────────────────────────────────────────┐
   │                  Android Tablet (USB)                   │
   │                                                         │
   │   [ Multi-Touch Grid (3x4 Layout) ]                     │
   │               │                                         │
   │               │ requestUnbufferedDispatch()             │
   │               ▼ (Bypasses Choreographer VSYNC Batching) │
   │   [ Direct onTouchEvent Hot Path ]                      │
   │               │                                         │
   │               │ Direct Socket Write (0 Thread Switches) │
   │               ▼                                         │
   │   [ 2-byte Binary Packet: Button ID (0-11) + State ]    │
   └───────────────────────────┬─────────────────────────────┘
                               │
               USB Cable via ADB Reverse Tunnel
               `adb reverse tcp:54321 tcp:54321` (< 0.5 ms RTT)
                               │
   ┌───────────────────────────▼─────────────────────────────┐
   │                       Linux Host                        │
   │                                                         │
   │   [ Native C rhythm-daemon (SCHED_RR Priority) ]        │
   │               │ TCP_NODELAY, TCP_QUICKACK, 2KB Buffer   │
   │               │ Zero-desync packet parser               │
   │               ▼                                         │
   │   [ Atomic write(2): EV_KEY + SYN_REPORT ]              │
   │               ▼                                         │
   │   [ /dev/uinput Virtual Keyboard Device ]               │
   │       ├── ID_INPUT_KEYBOARD=1 (Native Wayland / KWin)   │
   │       └── EV_REP Disabled (Zero auto-repeat chatter)    │
   │               ▼                                         │
   │   [ Rhythm Game (osu!mania, Clone Hero, Etterna, etc.) ]│
   └─────────────────────────────────────────────────────────┘
```

---

## ✨ Key Features

- **🎮 3x4 (12-Pad) Multi-Touch Grid:** 12 large, responsive pads arranged in 3 rows:
  - **Row 0 (Up):** Arrow chevron iconography (`▲`)
  - **Row 1 (Middle):** Target diamond iconography (`◆`)
  - **Row 2 (Down):** Downward chevron iconography (`▼`)
- **⚡ Sub-Millisecond Input Latency:** Zero thread switches, unbuffered hardware interrupts, and minimal kernel buffers.
- **🛡️ Zero Autorepeat & Chatter Suppression:** Custom kernel device disables `EV_REP` so held notes never trigger rapid keyboard repeat spam (`qqqqqqqq`).
- **🪟 Full Wayland & X11 Compatibility:** Registers the complete standard key range (1..248) so `systemd-udev` classifies it as `ID_INPUT_KEYBOARD=1`, routing inputs directly to active game windows.
- **📐 Interactive Layout Scaling:** Scale width & height from 50% to 100%, customize pad margins (4px to 24px), and choose vertical alignments (Top, Center, Bottom).
- **🎛️ Live In-App Key Rebinding:** Rebind any pad to any keyboard or gamepad key directly on the tablet screen with instant dynamic synchronization to Linux.
- **🔊 Low-Latency Hitsounds:** Toggleable mechanical switch, soft thock, or arcade pop sound with volume control and clipping headroom.
- **📳 Decoupled Haptic Feedback:** Vibrator engine runs asynchronously on background executors to prevent stalling touch interrupts.

---

## 🔬 Ultra-Low-Latency Engineering

1. **Bypassing Android Choreographer VSYNC:**
   Standard Android views batch touch events and delay delivery until the next display frame (11–16 ms delay at 60Hz). `GridTouchView` enables `requestUnbufferedDispatch(SOURCE_TOUCHSCREEN)`, feeding hardware digitizer interrupts straight to `onTouchEvent()` in real time.
2. **Zero-Context-Switch Direct Socket Path (< 5 µs):**
   Instead of queuing touch events through coroutines or background workers (which cost 2–10 ms in thread scheduling), `NetworkClient.sendEvent()` directly transmits bytes to the TCP output stream on line #1 of `onTouchEvent()`.
3. **Optimized Socket Options:**
   - `TCP_NODELAY`: Disables Nagle's algorithm post-handshake, preventing 40ms delayed transmission.
   - `TCP_QUICKACK`: Tells the Linux kernel to acknowledge incoming packets immediately.
   - `SO_RCVBUF / SO_SNDBUF`: Shrunk to minimal buffers (512B - 2KB) to eliminate bufferbloat.
4. **Linux Real-Time Scheduling (`SCHED_RR`):**
   The companion daemon elevates itself to real-time round-robin scheduling (`SCHED_RR`, priority 10), preempting non-real-time desktop processes.
5. **Zero Garbage Collection on Hot Path:**
   No objects, arrays, or iterators are allocated during touch events or drawing routines.

---

## 🚀 Quick Start (Automated Setup)

Clone this repository and run the automated setup script:

```bash
git clone https://github.com/Arda-codes/DTHController.git
cd DTHController
./setup.sh
```

### What `./setup.sh` does automatically:
1. Detects your distribution and installs any missing packages (`gcc`, `make`, `android-tools`).
2. Configures `/etc/udev/rules.d/99-uinput.rules` for non-root `/dev/uinput` access.
3. Adds your user to the `input` group and ensures the `uinput` kernel module is loaded on boot.
4. Compiles the native Linux daemon with `-O3` optimizations.
5. Checks your USB-connected Android tablet and flashes `rhythm-controller.apk` directly via ADB.

### 🎮 Playing:
Whenever you want to play, connect your tablet and run:

```bash
./start.sh
```
`./start.sh` automatically ensures `adb reverse` is active and launches the daemon!

---

## 🛠️ Manual Setup & Compilation

### 1. Build the Linux Daemon
Ensure you have `gcc` and `make` installed:
```bash
cd daemon
make clean && make
```

### 2. Configure `/dev/uinput` Permissions (Non-root)
To run the daemon without `sudo`:
```bash
echo 'KERNEL=="uinput", MODE="0660", GROUP="input", TAG+="uaccess"' | sudo tee /etc/udev/rules.d/99-uinput.rules
sudo udevadm control --reload-rules && sudo udevadm trigger
sudo usermod -aG input $USER
sudo modprobe uinput
```

### 3. Connect Tablet & Forward Port
Enable **USB Debugging** on your Android tablet:
```bash
adb reverse tcp:54321 tcp:54321
```

### 4. Build the Android APK (Optional)
If you wish to modify and build the APK yourself:
```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
*(A pre-compiled APK is already included in the root as [`rhythm-controller.apk`](rhythm-controller.apk)).*

---

## ⚙️ In-App Settings & Customization

Tap the **Gear Icon (`⚙️`)** in the top-left corner of the tablet screen:

| Setting | Options / Description |
| :--- | :--- |
| **Change Key Bindings** | Tap any button to rebind it to any key (Q-Z, 0-9, Space, Enter, Shift, Gamepad buttons). |
| **Button Width Scaling** | Scale grid width from 50% to 100% to fit your hand posture. |
| **Button Height Scaling**| Scale grid height from 50% to 100%. |
| **Button Spacing / Gap** | Tiny (4px), Normal (10px), Wide (16px), Extra Wide (24px). |
| **Vertical Alignment**   | Top, Center, or Bottom. |
| **Click Sound**          | Toggle instant hitsound on press. |
| **Sound Tone**           | **Mechanical Switch**, **Soft Thock**, or **Arcade Pop**. |
| **Sound Volume**         | 100%, 85%, 70%, 50%, 30%, 15%, 0% (Mute). |
| **Haptic Vibration**     | Toggle hardware tactile click vibration. |
| **Presets**              | Instant switch between **Default (QWER / ASDF / ZXCV)** and **4-Key (DFJK / Space)**. |

---

## 📡 Wire Protocol Specification

Communication between the tablet and the host daemon is carried over a raw TCP socket on `127.0.0.1:54321`.

### 1. Button Press / Release Event (2 Bytes)
```
[ Button ID (1 byte: 0..11) ] [ State (1 byte: 1=Down, 0=Up) ]
```

### 2. Dynamic Remap Packet (4 Bytes)
Sent whenever the user rebinds a key in settings:
```
[ 0xFE (1 byte) ] [ Button ID (1 byte) ] [ Keycode High (1 byte) ] [ Keycode Low (1 byte) ]
```

### 3. Default Button Layout
```
+--------------+--------------+--------------+--------------+
| ▲ UP 1 (0)   | ▲ UP 2 (1)   | ▲ UP 3 (2)   | ▲ UP 4 (3)   |
|   [KEY_Q]    |   [KEY_W]    |   [KEY_E]    |   [KEY_R]    |
+--------------+--------------+--------------+--------------+
| ◆ MID 1 (4)  | ◆ MID 2 (5)  | ◆ MID 3 (6)  | ◆ MID 4 (7)  |
|   [KEY_A]    |   [KEY_S]    |   [KEY_D]    |   [KEY_F]    |
+--------------+--------------+--------------+--------------+
| ▼ DOWN 1 (8) | ▼ DOWN 2 (9) | ▼ DOWN 3 (10)| ▼ DOWN 4 (11)|
|   [KEY_Z]    |   [KEY_X]    |   [KEY_C]    |   [KEY_V]    |
+--------------+--------------+--------------+--------------+
```

---

## 📱 Tablet Latency Optimization Guide

To achieve true physical minimum touch latency from your Android hardware:
1. **Enable Touch Sensitivity (Samsung / Android):**
   - Go to `Settings` → `Display` → Turn **Touch sensitivity** `ON`. This increases digitizer polling frequency and reduces touch debounce thresholds.
2. **Disable Game Booster / Touch Stabilization:**
   - If using Samsung Game Booster or Game Plugins, turn off "Accidental touch protection" and disable touch stabilization filters.
3. **Disable Power Saving Mode:**
   - Power saver throttles digitizer scan rates down to 60Hz. Ensure standard or high-performance refresh rate (90Hz / 120Hz) is enabled.

---

## ❓ Troubleshooting & FAQ

#### Q: Keystrokes are not registering in my game on Linux.
- **A:** Ensure the daemon is running and ADB reverse is configured:
  ```bash
  adb reverse tcp:54321 tcp:54321
  ```
  Check the connection dot in the top-right corner of the tablet: **Green = Connected**, **Red = Disconnected**.

#### Q: "Permission denied" when opening `/dev/uinput`.
- **A:** Run `./setup.sh` to install the udev rules, or manually add your user to the `input` group and reload udev:
  ```bash
  sudo usermod -aG input $USER
  ```
  Log out and log back in for group membership to apply.

#### Q: Tablet says "device unauthorized".
- **A:** Unlock your tablet and accept the USB debugging confirmation dialog. If it doesn't show up:
  ```bash
  adb kill-server && adb devices
  ```

---

## 📄 License
This project is licensed under the [MIT License](LICENSE).
