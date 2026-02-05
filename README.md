# AIChatBot-J2ME 📱

[![J2ME](https://img.shields.io/badge/Platform-J2ME_CLDC1.1%2FMIDP2.0-blue?logo=java&logoColor=yellow)](https://en.wikipedia.org/wiki/Java_Platform,_Micro_Edition)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![86997d2f-5108-4d05-9db2-6ce5b4c44665](https://github.com/user-attachments/assets/3dd8d5b1-157d-435a-ac12-0b6e9e597131)

**AI Chatbot for feature phones** - A Grok-powered coding assistant running on Nokia/Sony Ericsson devices with physical keypads. Works offline with ASCII art generation and full T9 input support.


## ✨ Features

- ✅ **True Nokia T9 input** - Standard layout (2=abc, 8=tuv) with 1s timeout
- ✅ **RSK Backspace** - Right Soft Key deletes characters instantly
- ✅ **Symbol wheel** - `*` key cycles 30+ programming symbols (`! : ; , < > * $ = ) _ - + ' " & % # / \ ( [ ] { } @ ~ ` ^ |`)
- ✅ **CAPS toggle** - Single `#` press switches case, double press shows history
- ✅ **Offline snippets** - 15+ Java ME code examples available without network
- ✅ **ASCII art** - `/image robot` generates text art (no image decoding needed)
- ✅ **Network resilient** - 2 retry attempts + timeout handling for GPRS/EDGE
- ✅ **Memory optimized** - 8KB history limit, RMS compression, no leaks

## 📱 Supported Devices

| Brand | Models | Notes |
|-------|--------|-------|
| **Nokia** | 3310 (3G), 5310, 6300, N70, N95 | Full T9 + RSK support |
| **Sony Ericsson** | K750i, W800i, K800i | Symbol wheel optimized |
| **Samsung** | E250, X820 | Tested on WTK emulator |
| **Motorola** | RAZR V3 | Limited to numeric input |

> 💡 Works on **any device running CLDC 1.1 + MIDP 2.0** (J2ME Polish compatible)

## ⚙️ Installation

1. Download `AIChatBot.jar`  from [Releases](https://github.com/your-username/AIChatBot-J2ME/releases)
2. Transfer to phone via:
   - Bluetooth .jar
   - USB cable (mass storage mode)
3. Launch from Applications menu

## 🔤 T9 Input Guide

| Key | Sequence | Result | Notes |
|-----|----------|--------|-------|
| `2` | 1 press | `a` | Standard Nokia layout |
| `2` | 2 presses | `b` | |
| `2` | 3 presses | `c` | |
| `2` | 4 presses | `2` | Digit after letters |
| `8` | 1 press | `t` | **Now fully functional** (no scroll conflict) |
| `8` | 2 presses | `u` | |
| `8` | 3 presses | `v` | |
| `8` | 4 presses | `8` | |
| `*` | Cycle | `!` → `:` → `;` → ... | 30+ symbols for coding |
| `#` | 1 press | Toggle CAPS | `a` → `A` |
| `#` | 2 presses (<400ms) | Show history | Last 15 messages |
| **RSK** | Press | ← Backspace | Right Soft Key (always accessible) |
| `0` | 2 presses | New line | For code formatting |

## 🖼️ Screenshots

| Boot Screen | T9 Input | Symbol Wheel |
|-------------|----------|--------------|
| ![Boot](docs/SCREENSHOTS/boot_screen.png) | ![T9](docs/SCREENSHOTS/t9_input.png) | ![Symbols](docs/SCREENSHOTS/symbols_menu.png) |

*(Generate these with WTK 2.5.2 → Device Selector → Take Screenshot)*

## 🛠️ Build from Source

### Requirements
- Sun Java Wireless Toolkit 2.5.2
- JDK 8 (for WTK compatibility)
- Apache Ant 1.10+

### Build steps
```bash
# Clone repo
git clone https://github.com/your-username/AIChatBot-J2ME.git
cd AIChatBot-J2ME

# Build with Ant (uses WTK 2.5.2)
ant clean
ant build
