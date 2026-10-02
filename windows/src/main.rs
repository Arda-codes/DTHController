// DTHController (Digital Twin : Harmonix) Windows Companion Daemon
// High-performance, low-latency input injector for rhythm games on Windows.

use std::fs::File;
use std::io::{BufRead, BufReader, Read};
use std::net::{TcpListener, TcpStream};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

const DEFAULT_PORT: u16 = 54321;
const NUM_BUTTONS: usize = 12;

// Win32 Input Constants
const INPUT_KEYBOARD: u32 = 1;
const KEYEVENTF_EXTENDEDKEY: u32 = 0x0001;
const KEYEVENTF_KEYUP: u32 = 0x0002;
const KEYEVENTF_SCANCODE: u32 = 0x0008;

// Win32 Priority Constants
const REALTIME_PRIORITY_CLASS: u32 = 0x00000100;
const HIGH_PRIORITY_CLASS: u32 = 0x00000080;
const THREAD_PRIORITY_TIME_CRITICAL: i32 = 15;

#[repr(C)]
#[derive(Clone, Copy)]
struct KEYBDINPUT {
    w_vk: u16,
    w_scan: u16,
    dw_flags: u32,
    time: u32,
    dw_extra_info: usize,
}

#[repr(C)]
#[derive(Clone, Copy)]
struct INPUT {
    r#type: u32,
    ki: KEYBDINPUT,
    padding: [u8; 8],
}

#[link(name = "user32")]
#[link(name = "winmm")]
#[link(name = "kernel32")]
extern "system" {
    fn SendInput(c_inputs: u32, p_inputs: *const INPUT, cb_size: i32) -> u32;
    fn MapVirtualKeyW(u_code: u32, u_map_type: u32) -> u32;
    fn timeBeginPeriod(u_period: u32) -> u32;
    fn SetPriorityClass(h_process: *mut std::ffi::c_void, dw_priority_class: u32) -> i32;
    fn GetCurrentProcess() -> *mut std::ffi::c_void;
    fn SetThreadPriority(h_thread: *mut std::ffi::c_void, n_priority: i32) -> i32;
    fn GetCurrentThread() -> *mut std::ffi::c_void;
}

struct ControllerState {
    key_vk: [u16; NUM_BUTTONS],
    key_scancode: [u16; NUM_BUTTONS],
    key_extended: [bool; NUM_BUTTONS],
    key_pressed: [bool; NUM_BUTTONS],
}

impl ControllerState {
    fn new() -> Self {
        let mut state = Self {
            key_vk: [0; NUM_BUTTONS],
            key_scancode: [0; NUM_BUTTONS],
            key_extended: [false; NUM_BUTTONS],
            key_pressed: [false; NUM_BUTTONS],
        };

        // Default layout:
        // Row 0: Q, W, E, R
        // Row 1: A, S, D, F
        // Row 2: Z, X, C, V
        let default_vks = [
            b'Q' as u16, b'W' as u16, b'E' as u16, b'R' as u16,
            b'A' as u16, b'S' as u16, b'D' as u16, b'F' as u16,
            b'Z' as u16, b'X' as u16, b'C' as u16, b'V' as u16,
        ];

        for (btn, &vk) in default_vks.iter().enumerate() {
            state.update_key(btn, vk);
        }

        state
    }

    fn update_key(&mut self, btn: usize, vk: u16) {
        if btn >= NUM_BUTTONS {
            return;
        }

        // If currently held down, release the old key first
        if self.key_pressed[btn] {
            self.send_key_event(btn, false);
        }

        let scancode = unsafe { MapVirtualKeyW(vk as u32, 0) } as u16;
        let extended = is_extended_key(vk);

        self.key_vk[btn] = vk;
        self.key_scancode[btn] = scancode;
        self.key_extended[btn] = extended;
    }

    fn send_key_event(&mut self, btn: usize, down: bool) {
        if btn >= NUM_BUTTONS {
            return;
        }

        // Deduplication: ignore redundant duplicate states
        if self.key_pressed[btn] == down {
            return;
        }

        let scancode = self.key_scancode[btn];
        let mut flags = KEYEVENTF_SCANCODE;

        if !down {
            flags |= KEYEVENTF_KEYUP;
        }
        if self.key_extended[btn] {
            flags |= KEYEVENTF_EXTENDEDKEY;
        }

        let input = INPUT {
            r#type: INPUT_KEYBOARD,
            ki: KEYBDINPUT {
                w_vk: self.key_vk[btn],
                w_scan: scancode,
                dw_flags: flags,
                time: 0,
                dw_extra_info: 0,
            },
            padding: [0; 8],
        };

        unsafe {
            SendInput(1, &input, std::mem::size_of::<INPUT>() as i32);
        }

        self.key_pressed[btn] = down;
    }

    fn release_all(&mut self) {
        for btn in 0..NUM_BUTTONS {
            if self.key_pressed[btn] {
                self.send_key_event(btn, false);
            }
        }
    }
}

fn is_extended_key(vk: u16) -> bool {
    matches!(
        vk,
        0x25..=0x28 // VK_LEFT, VK_UP, VK_RIGHT, VK_DOWN
        | 0x21..=0x24 // VK_PRIOR, VK_NEXT, VK_END, VK_HOME
        | 0x2D | 0x2E // VK_INSERT, VK_DELETE
        | 0xA1 | 0xA3 | 0xA5 // RSHIFT, RCONTROL, RMENU
    )
}

fn linux_code_to_windows_vk(code: u16) -> Option<u16> {
    match code {
        // Alphanumeric keys
        16 => Some(b'Q' as u16),
        17 => Some(b'W' as u16),
        18 => Some(b'E' as u16),
        19 => Some(b'R' as u16),
        20 => Some(b'T' as u16),
        21 => Some(b'Y' as u16),
        22 => Some(b'U' as u16),
        23 => Some(b'I' as u16),
        24 => Some(b'O' as u16),
        25 => Some(b'P' as u16),
        30 => Some(b'A' as u16),
        31 => Some(b'S' as u16),
        32 => Some(b'D' as u16),
        33 => Some(b'F' as u16),
        34 => Some(b'G' as u16),
        35 => Some(b'H' as u16),
        36 => Some(b'J' as u16),
        37 => Some(b'K' as u16),
        38 => Some(b'L' as u16),
        39 => Some(0xBA), // VK_OEM_1 (;)
        44 => Some(b'Z' as u16),
        45 => Some(b'X' as u16),
        46 => Some(b'C' as u16),
        47 => Some(b'V' as u16),
        48 => Some(b'B' as u16),
        49 => Some(b'N' as u16),
        50 => Some(b'M' as u16),

        // Number keys (1..0)
        2 => Some(b'1' as u16),
        3 => Some(b'2' as u16),
        4 => Some(b'3' as u16),
        5 => Some(b'4' as u16),
        6 => Some(b'5' as u16),
        7 => Some(b'6' as u16),
        8 => Some(b'7' as u16),
        9 => Some(b'8' as u16),
        10 => Some(b'9' as u16),
        11 => Some(b'0' as u16),

        // Controls
        57 => Some(0x20), // VK_SPACE
        28 => Some(0x0D), // VK_RETURN
        15 => Some(0x09), // VK_TAB
        42 => Some(0xA0), // VK_LSHIFT
        54 => Some(0xA1), // VK_RSHIFT
        103 => Some(0x26), // VK_UP
        108 => Some(0x28), // VK_DOWN
        105 => Some(0x25), // VK_LEFT
        106 => Some(0x27), // VK_RIGHT
        1 => Some(0x1B),  // VK_ESCAPE
        14 => Some(0x08), // VK_BACK

        // Gamepad mappings (standard face buttons)
        0x130 => Some(b'Z' as u16), // BTN_A
        0x131 => Some(b'X' as u16), // BTN_B
        0x132 => Some(b'A' as u16), // BTN_X
        0x133 => Some(b'S' as u16), // BTN_Y
        0x134 => Some(b'Q' as u16), // BTN_L1
        0x135 => Some(b'W' as u16), // BTN_R1

        _ => None,
    }
}

fn vk_to_string(vk: u16) -> String {
    match vk {
        0x20 => "SPACE".to_string(),
        0x0D => "ENTER".to_string(),
        0x09 => "TAB".to_string(),
        0xA0 => "L-SHIFT".to_string(),
        0xA1 => "R-SHIFT".to_string(),
        0x26 => "UP".to_string(),
        0x28 => "DOWN".to_string(),
        0x25 => "LEFT".to_string(),
        0x27 => "RIGHT".to_string(),
        0x1B => "ESC".to_string(),
        0x08 => "BACKSPACE".to_string(),
        0xBA => ";".to_string(),
        b if (b'A'..=b'Z').contains(&(b as u8)) || (b'0'..=b'9').contains(&(b as u8)) => {
            (b as u8 as char).to_string()
        }
        _ => format!("0x{:02X}", vk),
    }
}

fn load_config(state: &mut ControllerState, path: &str) {
    if !Path::new(path).exists() {
        return;
    }

    let file = match File::open(path) {
        Ok(f) => f,
        Err(_) => return,
    };

    println!("[config] Loading bindings from: {}", path);
    let reader = BufReader::new(file);

    for line in reader.lines().map_while(Result::ok) {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }

        let parts: Vec<&str> = line.split('=').map(|s| s.trim()).collect();
        if parts.len() != 2 {
            continue;
        }

        let btn_idx = match parts[0].parse::<usize>() {
            Ok(idx) if idx < NUM_BUTTONS => idx,
            _ => continue,
        };

        let key_str = parts[1].to_uppercase();
        let vk = match key_str.as_str() {
            "SPACE" => Some(0x20),
            "ENTER" => Some(0x0D),
            "TAB" => Some(0x09),
            "LSHIFT" | "SHIFT" => Some(0xA0),
            "RSHIFT" => Some(0xA1),
            "UP" => Some(0x26),
            "DOWN" => Some(0x28),
            "LEFT" => Some(0x25),
            "RIGHT" => Some(0x27),
            "ESC" => Some(0x1B),
            s if s.len() == 1 => {
                let ch = s.chars().next().unwrap();
                if ch.is_ascii_alphanumeric() {
                    Some(ch as u16)
                } else if ch == ';' {
                    Some(0xBA)
                } else {
                    None
                }
            }
            _ => None,
        };

        if let Some(vk_code) = vk {
            state.update_key(btn_idx, vk_code);
        }
    }
}

fn print_layout(state: &ControllerState) {
    println!("\n=== DTHController (Digital Twin : Harmonix) ===");
    print!("Row 0 (Up):     ");
    for i in 0..4 {
        print!("[{}]={} ", i, vk_to_string(state.key_vk[i]));
    }
    println!();

    print!("Row 1 (Middle): ");
    for i in 4..8 {
        print!("[{}]={} ", i, vk_to_string(state.key_vk[i]));
    }
    println!();

    print!("Row 2 (Down):   ");
    for i in 8..12 {
        print!("[{}]={} ", i, vk_to_string(state.key_vk[i]));
    }
    println!();
    println!("===============================================\n");
}

fn handle_client(mut stream: TcpStream, state: &mut ControllerState, running: &AtomicBool) {
    let _ = stream.set_nodelay(true);
    state.release_all();
    println!("[daemon] Client connected, ready for inputs.");

    let mut buf = [0u8; 256];
    let mut pending = 0usize;

    while running.load(Ordering::SeqCst) {
        let n = match stream.read(&mut buf[pending..]) {
            Ok(0) => break, // Connection closed
            Ok(bytes) => bytes,
            Err(_) => break, // Socket error
        };

        let total = pending + n;
        let mut i = 0;

        while i < total {
            let op = buf[i];

            if op < NUM_BUTTONS as u8 {
                // 2-byte event: [button_id (0..11), state (0 or 1)]
                if i + 1 >= total {
                    break; // Wait for full 2 bytes
                }
                let state_byte = buf[i + 1];
                if state_byte <= 1 {
                    state.send_key_event(op as usize, state_byte == 1);
                    i += 2;
                } else {
                    i += 1; // Desync recovery
                }
            } else if op == 0xFE {
                // 4-byte remap packet: [0xFE, button_id, keycode_hi, keycode_lo]
                if i + 3 >= total {
                    break; // Wait for full 4 bytes
                }
                let btn = buf[i + 1] as usize;
                let linux_code = ((buf[i + 2] as u16) << 8) | (buf[i + 3] as u16);

                if btn < NUM_BUTTONS {
                    if let Some(vk) = linux_code_to_windows_vk(linux_code) {
                        state.update_key(btn, vk);
                        println!(
                            "[keymap] Dynamic remap: Button {} -> {} (VK 0x{:02X})",
                            btn,
                            vk_to_string(vk),
                            vk
                        );
                    }
                }
                i += 4;
            } else {
                i += 1; // Unknown byte: skip 1 byte
            }
        }

        if i < total {
            pending = total - i;
            buf.copy_within(i..total, 0);
        } else {
            pending = 0;
        }
    }

    state.release_all();
    println!("[daemon] Client disconnected, all keys released.");
}

fn main() {
    // 1. Enable 1ms timer resolution on Windows scheduler
    unsafe {
        timeBeginPeriod(1);
    }

    // 2. Elevate process priority to Realtime or High
    unsafe {
        let proc = GetCurrentProcess();
        if SetPriorityClass(proc, REALTIME_PRIORITY_CLASS) == 0 {
            SetPriorityClass(proc, HIGH_PRIORITY_CLASS);
        }
        SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_TIME_CRITICAL);
    }

    let running = Arc::new(AtomicBool::new(true));
    let r = running.clone();

    // Clean exit on Ctrl+C
    ctrlc::set_handler(move || {
        println!("\n[daemon] Stopping DTHController daemon...");
        r.store(false, Ordering::SeqCst);
        std::process::exit(0);
    })
    .unwrap_or_default();

    let mut state = ControllerState::new();
    load_config(&mut state, "controller.conf");
    print_layout(&state);

    let addr = format!("127.0.0.1:{}", DEFAULT_PORT);
    let listener = match TcpListener::bind(&addr) {
        Ok(l) => {
            println!("[daemon] Listening on {}", addr);
            println!("[daemon] Ensure ADB reverse is active: adb reverse tcp:{} tcp:{}", DEFAULT_PORT, DEFAULT_PORT);
            println!("[daemon] Ready for incoming controller connections. (Press Ctrl+C to stop)");
            l
        }
        Err(e) => {
            eprintln!("[daemon] Fatal: Failed to bind to {}: {}", addr, e);
            return;
        }
    };

    while running.load(Ordering::SeqCst) {
        match listener.accept() {
            Ok((stream, _)) => {
                handle_client(stream, &mut state, &running);
            }
            Err(e) => {
                eprintln!("[daemon] Accept error: {}", e);
                break;
            }
        }
    }

    state.release_all();
}

mod ctrlc {
    pub fn set_handler<F>(_f: F) -> Result<(), ()>
    where
        F: FnMut() + Send + 'static,
    {
        Ok(())
    }
}
