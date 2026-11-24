# Building MouseSense Server (IT Amadersomaj Inc)

MouseSense Server uses a cross-platform CMake build system supporting both Windows and Linux.

---

## Prerequisites

### **Windows (Visual Studio + vcpkg)**
* **IDE / Build Tools:** Visual Studio 2022/2026 or Visual Studio Build Tools with C++ workload.
* **Package Manager:** [vcpkg](https://github.com/microsoft/vcpkg) is integrated for dependency management.
* **CMake:** Included with Visual Studio or installed separately.

### **Linux (Ubuntu/Debian)**
* **Compiler:** GCC 11+ or Clang.
* **Build Tools:** CMake and Make.
* **Dependencies:** `asio` and `wxWidgets 3.3`.

---

## Build Instructions

### **Windows**

1. **Configure and Build via CMake**
   ```powershell
   cmake -B build -G "Visual Studio 18 2026" -A x64 -DCMAKE_TOOLCHAIN_FILE="<vcpkg_root>/scripts/buildsystems/vcpkg.cmake"
   cmake --build build --config Release
   ```

2. **Standalone Binary & Distribution**
   The compiled executable `MouseSense.exe` and its dependencies are distributed in:
   `MouseSense_PC/`

---

### **Linux**

1. **Install Dependencies**
   ```bash
   sudo apt update
   sudo apt install build-essential cmake libwxgtk3.3-dev libasio-dev adb
   ```

2. **Configure & Compile**
   ```bash
   cmake -B build -DCMAKE_BUILD_TYPE=Release
   cd build
   make
   ```

3. **Deploy**
   ```bash
   ./release.sh
   ./install.sh
   ```

---

## Network Architecture

* **UDP Port 6969:** Auto-discovery beacon (`MOUSESENSE_BEACON`) and low-latency mouse/keyboard event stream.
* **TCP Port 6969:** Handshake and connection state management.
* **ADB Port 6969:** Reverse port forwarding for wired USB mode.

---

&copy; 2026 **IT Amadersomaj Inc**. All rights reserved.
