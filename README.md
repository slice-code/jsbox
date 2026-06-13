# JS-Sandbox (JSBox) - Android Runtime & Web IDE

JS-Sandbox is a robust runtime platform and integrated development environment (IDE) on Android devices to build, test, and execute DOM-based JavaScript applications with a native Android bridge.

---

## 📱 Store Listing Description

### Short Description
> Build and execute JavaScript DOM applications directly on your phone with native Android API access for vibration, alarms, and background services.

### Long Description
**JS-Sandbox** turns your Android device into a portable JavaScript programming laboratory. Write standard JavaScript DOM code, run it inside a high-speed WebView sandbox container, and interact directly with the Android operating system using our high-performance native bridge.

---

## ⚡ Key Features

*   **🗂️ Project Workspace Dashboard**: Easily manage multiple code projects. Customize project descriptions, names, and icons (supports direct image uploads from the gallery).
*   **💻 Remote Web IDE (Local Server)**: Run a local server directly on your phone on port `5009` (Wi-Fi access). Comfortably edit your project files wirelessly from your PC browser!
*   **🔄 Instant Run (Hot-Reloading)**: Instantly push code changes wirelessly from the PC Web IDE to your phone via WebSockets without needing to restart the app.
*   **✏️ Integrated Sora Code Editor**: Write code directly on your phone using a premium native Android code editor featuring auto-save, line numbers, and developer-friendly formatting.
*   **🔗 Native Android Bridge APIs**: Access native Android hardware modules directly from your JavaScript scripts:
    *   `showToast(msg)` — Triggers a native toast message.
    *   `vibrate(ms)` — Vibrates the device with a custom duration.
    *   `createAlarm(label, hour, minute)` — Schedules system alarms.
    *   `startBackgroundService(filename)` — Runs background scripts persistently.
*   **⚙️ Headless Background Service**: Run headless scripts (such as `service.js`) persistently in the background using an Android Foreground Service.
*   **🐞 Live Console Log Debugger**: A built-in console debug panel at the bottom of the runner screen to trace `console.log()` statements and JS runtime errors in real time.

---

## 🚀 How to Run Locally

### Prerequisites
*   **Android Studio** (Koala or newer)
*   **JDK 17 / 21**

### Steps:
1.  Open Android Studio.
2.  Select **Open** and choose this project directory.
3.  Let Android Studio sync Gradle (using Gradle `9.3.1`).
4.  Ensure you have configured your local [.env](file:///home/gugus/Documents/Project/jsbox/.env) file.
5.  Run the app on an Emulator or a physical Android device.
