# AI ChatBot 
## 📱 Overview

**AI ChatBot ** is an AI-powered chat application built for J2ME (Java 2 Micro Edition) phones. It delivers a smooth messaging experience with an optimized T9 input system and a virtual QWERTY keyboard.

---
[![J2ME](https://img.shields.io/badge/Platform-J2ME_CLDC1.1%2FMIDP2.0-blue?logo=java&logoColor=yellow)](https://en.wikipedia.org/wiki/Java_Platform,_Micro_Edition)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)



![86997d2f-5108-4d05-9db2-6ce5b4c44665 (4)](https://github.com/user-attachments/assets/eba6d160-03b4-45ac-b9d7-132a62998000)

## 🎯 Main Features

### 💬 Smart Chat

* **Context-aware conversation**: The AI remembers previous messages
* **Real-time responses**: Loading indicator during processing
* **Bubble interface**: User messages (right) and AI messages (left)
* **Smooth scrolling**: Navigate chat history with the D-pad

### ⌨️ Advanced T9 Input System

* **Nokia-style multi-tap**: Press multiple times to change letters
* **Live preview**: Displays available characters
* **3 input modes**:

  * `[abc]` – lowercase
  * `[ABC]` – uppercase
  * `[123]` – numeric
* **30+ symbols**: Quick access to special characters
* **Command history**: Browse last 15 messages

### 🖥️ Virtual QWERTY Keyboard

* **Auto-detection**: Switches to T9 when typing numbers
* **Directional navigation**: Use D-pad to select keys
* **Caps mode**: Toggle with `*`
* **Optimized layout**:

```
1 2 3 4 5 6 7 8 9 0
q w e r t y u i o p
a s d f g h j k l
z x c v b n m
[SPACE] [.] [<--]
```

### 🔍 Web Search

* **Direct queries** for real-time topics
* **Seamless integration** with chat interface
* **Visual tag**: Results marked with `[Web]`

### 📝 History Management

* **Auto-save** all conversations
* **Multiple sessions** support
* **Chronological display** with timestamps
* **Quick preview list**

### 💾 Multi-format Export

* **TXT**: Plain text with metadata
* **PNG**: Simulated image export
* **RMS**: Native J2ME storage format

### ⚙️ Custom Settings

* **Timeout**: 5–120 seconds (default 30s)
* **AI context**: Enable/disable memory
* **Context size**: 1–20 exchanges (default 5)
* **Persistent settings**

---

## 🎮 Controls Guide

### T9 Mode (Default)

| Key     | Function                   | Description |
| ------- | -------------------------- | ----------- |
| **0**   | Space / 0 / New line       |             |
| **1**   | Punctuation (30+ symbols)  |             |
| **2–9** | Multi-tap letters          |             |
| **#**   | Toggle `[abc]` / `[ABC]`   |             |
| *****   | Toggle `[abc]` / `[123]`   |             |
| ******  | Double press → QWERTY mode |             |

### Navigation

| Action    | Function         |
| --------- | ---------------- |
| D-pad ↑/↓ | Scroll messages  |
| D-pad ←/→ | Browse history   |
| Fire (5)  | Send message     |
| LSK       | Open menu        |
| RSK       | Delete character |

### QWERTY Mode

| Action | Function                   |
| ------ | -------------------------- |
| D-pad  | Navigate keyboard          |
| Fire   | Select key                 |
| *      | Toggle caps                |
| #      | Return to T9               |
| 0–9    | Auto-switch to T9 + number |

---

## 📋 Menu Structure

### Main Menu

```
AI ChatBot v1.1
1. New Chat
2. History
3. Settings
4. About
5. Exit
User ID: USRxxxx
```

### Context Menu (LSK)

```
Send
Search
Clear
Save
New
Back
```

---

## 🎨 User Interface

* Chat bubbles (AI left, user right)
* Input field with live T9 preview
* Visual indicators:

  * `[abc]`, `[ABC]`, `[123]`, `QW`
  * `●●●●` = AI loading
  * `_` = cursor

---

## 🔧 Advanced Features

### Context System

AI remembers previous exchanges for better answers.

### Command History

Use ← / → to browse last 15 messages.

### Real-time T9 Preview

Shows current character selection while typing.

### Auto-commit

Character is confirmed:

* After 800ms
* On key change
* On send
* On navigation

---

## 💾 Save Formats

* **TXT**: Full readable chat log
* **PNG**: Simulated export format
* **RMS**: Native structured storage

---

## 📊 Technical Specs

* **Platform**: CLDC 1.1 / MIDP 2.0
* **Encoding**: CP1252 (no emojis)
* **Screen support**: 128×160 → 240×320+ (adaptive)

### Architecture

* MIDlet main app
* Canvas-based UI screens
* RMS storage system
* Network API handler

### API

* Endpoint: `/api/ai/chatgpt`
* Method: GET
* Param: `text`
* Response: JSON (`result`, `answer`, etc.)

---

## 🎨 Color Theme (Matrix)

* Dark green background
* Bright green text
* Cyan-green user messages
* Low-light borders and accents

---

## 🚀 Quick Start

1. Launch app
2. Select **New Chat**
3. Type message (T9 or QWERTY)
4. Press **5 (Fire)** to send
5. Continue chatting (context saved automatically)

---

## ❓ FAQ

* **Context memory?** Configurable (1–20 messages)
* **Offline use?** No (internet required)
* **History saved?** Yes (RMS storage)
* **QWERTY battery?** Same as T9

---

## 🐛 Troubleshooting

* **HTTP 403** → Wait and retry
* **Connection error** → Check network / increase timeout
* **Weird characters** → Auto-cleaned to `?`
* **T9 issues** → Wait 800ms between presses
* **QWERTY not working** → Double press `*` quickly

---

## 📝 Version Notes

### v1.1

* Full T9 system
* Virtual QWERTY
* Chat history + context
* Export formats
* Web search
* Small screen support

### Future (v1.2+)

* Predictive T9
* Custom themes
* Shortcuts
* Draft mode
* Multi-language

---

## 👨‍💻 Developer Info

### Files

* AIChatBot.java
* Utils.java
* History.java
* Settings.java
* SaveManager.java

### Extend

* Add menu commands
* Customize T9 mapping
* Add new export formats

---

## 📄 License

Open-source J2ME application
Encoding: CP1252
API: ChatGPT (Workers API)

© 2024 AI ChatBot Project

---

## 📞 Support

* Version: v1.1
* Platform: J2ME (CLDC 1.1 / MIDP 2.0)

**Status:** Stable ✅
