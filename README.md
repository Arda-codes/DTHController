# DTHController (Digital Twin : Harmonix)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Platform: Linux](https://img.shields.io/badge/Platform-Linux%20(X11%20%2F%20Wayland)-orange.svg)](https://kernel.org)
[![Android: 8.0+](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)

DTHController turns an Android tablet into a low-latency Linux game controller over USB. It provides a 12-pad touch interface on Android and a native C companion daemon that exposes a `/dev/uinput` keyboard device on Linux, suitable for rhythm games such as osu!mania, Clone Hero, and Etterna.

---

## Table of Contents
- [Architecture](#architecture)
- [Features](#features)
- [Latency Optimizations](#latency-optimizations)
- [Quick Start](#quick-start)
- [Manual Setup](#manual-setup)
- [In-App Settings](#in-app-settings)
- [Wire Protocol](#wire-protocol)
- [Tablet Configuration](#tablet-configuration)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## Architecture

```
   ┌─────────────────────────────────────────────────────────┐
   │                  Android Tablet (USB)                   │
   │                                                         │
   │   Multi-touch grid (3x4 layout)                         │
   │               │                                         │
   │               │ requestUnbufferedDispatch()             │
   │               ▼                                         │
   │   Direct onTouchEvent handler                           │
   │               │                                         │
   │               │ Direct socket write (< 5 µs)            │
   │               ▼                                         │
   │   2-byte binary packet: [button_id, state]              │
   └───────────────────────────┬─────────────────────────────┘
                               │
               USB cable via adb reverse tunnel
               `adb reverse tcp:54321 tcp:54321` (< 0.5 ms RTT)
                               │
   ┌───────────────────────────▼─────────────────────────────┐
   │                       Linux Host                        │
   │                                                         │
   │   C companion daemon (SCHED_RR real-time priority)      │
   │               │ TCP_NODELAY, TCP_QUICKACK, 2KB buffer   │
   │               │ Desync-safe packet parser               │
   │               ▼                                         │
   │   Atomic write(2): [EV_KEY, SYN_REPORT]                 │
   │               ▼                                         │
   │   /dev/uinput virtual keyboard device                   │
   │       ├── ID_INPUT_KEYBOARD=1 (Wayland & X11 routed)    │
   │       └── EV_REP disabled (no autorepeat chatter)       │
   │               ▼                                         │
   │   Game (osu!mania, Clone Hero, Etterna, etc.)           │
   └─────────────────────────────────────────────────────────┘
```

---

## Features

- **12 touch pads (3x4):** Arranged in three rows with direction indicators:
  - Row 0 (Up): Chevron icons (`▲`)
  - Row 1 (Middle): Diamond icons (`◆`)
  - Row 2 (Down): Downward chevron icons (`▼`)
- **No autorepeat spam:** The uinput device runs without `EV_REP`, preventing the kernel from repeating held keys during hold notes.
- **Wayland and X11 support:** Registers keys 1 to 248 so systemd-udev classifies the device with `ID_INPUT_KEYBOARD=1`, routing keystrokes directly to focused windows.
- **Adjustable pad scaling:** Scale pad width and height from 50% to 100%, customize pad margins (4px to 24px), and align pads to the top, center, or bottom of the screen.
- **Key rebinding:** Rebind any pad to any keyboard or gamepad key directly from the tablet interface. Changes synchronize immediately with the daemon.
- **Hitsound feedback:** Optional mechanical switch, soft thock, or arcade pop sound with volume control and headroom limits to prevent clipping.
- **Decoupled haptics:** Tactile vibration runs on a background executor so vibration calls never delay touch packet transmission.

---

## Latency Optimizations

1. **Unbuffered touch dispatch:**
   Android usually delays touch events until display VSYNC (11 to 16 ms at 60 Hz). DTHController calls `requestUnbufferedDispatch(SOURCE_TOUCHSCREEN)` so hardware interrupts reach `onTouchEvent()` immediately.
2. **Direct socket writes (< 5 µs):**
   Instead of queuing events into background coroutines or handlers, `NetworkClient.sendEvent()` writes directly to the TCP socket stream inside `onTouchEvent()`.
3. **Socket options:**
   - `TCP_NODELAY`: Disables Nagle's algorithm after connection, eliminating 40 ms packet delays.
   - `TCP_QUICKACK`: Forces immediate TCP acknowledgments on Linux.
   - `SO_RCVBUF` and `SO_SNDBUF`: Set to minimal buffers (512 B to 2 KB) to prevent queue buffering.
4. **Real-time Linux priority:**
   The daemon runs with `SCHED_RR` (priority 10) to preempt background desktop processes.
5. **Zero allocations on the hot path:**
   No objects, arrays, or iterators are allocated during touch handling or canvas drawing.

---

## Quick Start

Clone the repository and run the setup script:

```bash
git clone https://github.com/Arda-codes/DTHController.git
cd DTHController
./setup.sh
```

`setup.sh` performs the following steps:
1. Installs missing dependencies (`gcc`, `make`, `adb`) via your distribution package manager.
2. Creates `/etc/udev/rules.d/99-uinput.rules` for non-root uinput access and loads the kernel module.
3. Compiles the Linux daemon with `-O3` optimizations.
4. Checks for a connected Android tablet and installs `rhythm-controller.apk`.

To launch the controller:
```bash
./start.sh
```
`start.sh` sets up port forwarding (`adb reverse tcp:54321 tcp:54321`) and starts `rhythm-daemon`.

---

## Manual Setup

### 1. Compile the Linux Daemon
Requires `gcc` and `make`:
```bash
cd daemon
make clean && make
```

### 2. Configure /dev/uinput Permissions
To run without `sudo`:
```bash
echo 'KERNEL=="uinput", MODE="0660", GROUP="input", TAG+="uaccess"' | sudo tee /etc/udev/rules.d/99-uinput.rules
sudo udevadm control --reload-rules && sudo udevadm trigger
sudo usermod -aG input $USER
sudo modprobe uinput
```

### 3. Connect Tablet and Forward Port
Enable USB debugging on the tablet, connect the cable, and run:
```bash
adb reverse tcp:54321 tcp:54321
```

### 4. Install the Android App
Install the included APK:
```bash
adb install -r rhythm-controller.apk
```

Or build from source:
```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## In-App Settings

Tap the gear icon in the top-left corner of the tablet screen:

| Setting | Options / Description |
| :--- | :--- |
| **Change Key Bindings** | Rebind any pad to any key (alphanumeric, arrows, modifiers, gamepad buttons). |
| **Button Width Scaling** | 50% to 100% horizontal width scale. |
| **Button Height Scaling**| 50% to 100% vertical height scale. |
| **Button Spacing / Gap** | Tiny (4px), Normal (10px), Wide (16px), or Extra Wide (24px). |
| **Vertical Alignment**   | Top, Center, or Bottom. |
| **Click Sound**          | On or Off. |
| **Sound Tone**           | Mechanical Switch, Soft Thock, or Arcade Pop. |
| **Sound Volume**         | 100%, 85%, 70%, 50%, 30%, 15%, or 0% (Mute). |
| **Haptic Vibration**     | On or Off. |
| **Presets**              | Default (QWER / ASDF / ZXCV) or 4-Key (DFJK / Space). |

---

## Wire Protocol

The tablet streams events to `127.0.0.1:54321` over a raw TCP connection.

### Button Events (2 Bytes)
```
[ button_id: uint8 (0..11) ] [ state: uint8 (1=down, 0=up) ]
```

### Dynamic Remapping (4 Bytes)
Sent when a binding changes in settings:
```
[ 0xFE: uint8 ] [ button_id: uint8 ] [ keycode_hi: uint8 ] [ keycode_lo: uint8 ]
```

### Default Layout
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

## Tablet Configuration

For lowest hardware latency:
1. **Enable touch sensitivity:**
   On Samsung devices, go to `Settings` > `Display` and turn on **Touch sensitivity** to raise digitizer polling frequency.
2. **Disable touch filters:**
   In Samsung Game Booster or Game Plugins, turn off **Accidental touch protection** and disable touch stabilization filters.
3. **Turn off power saving:**
   Power saving modes drop digitizer scan rates to 60 Hz. Use standard or 120 Hz refresh rates.

---

## Troubleshooting

#### Keystrokes do not register in game
Verify the daemon is running and adb reverse is active:
```bash
adb reverse tcp:54321 tcp:54321
```
Check the status indicator in the top-right corner of the tablet: green means connected, red means disconnected.

#### Permission denied on `/dev/uinput`
Add your user to the input group and reload udev:
```bash
sudo usermod -aG input $USER
sudo udevadm control --reload-rules && sudo udevadm trigger
```
Log out and log back in to apply group changes.

#### Device unauthorized in ADB
Unlock the tablet and accept the USB debugging dialog. If the dialog does not appear:
```bash
adb kill-server && adb devices
```

---

## License
MIT License. See [LICENSE](LICENSE) for details.
