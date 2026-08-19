# 🚀 EasyHiddify Lib

[![JitPack](https://jitpack.io/v/YaslikS/easy_hiddify_lib.svg)](https://jitpack.io/#YaslikS/easy_hiddify_lib)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org)

**EasyHiddify Lib** is a simple Android library for quick integration of a VPN client based on **Hiddify Core (Libbox / sing-box)**.

The library handles all the heavy lifting: configuration parsing, `VpnService` lifecycle management, application Split Tunneling, and real-time inter-process status/log streaming.

---

## ✨ Features

- ⚡ **Quick Integration:** Launch VPN in just a couple of lines of code.
- 🔒 **Protocol Support:**
  - **VLESS** (with REALITY, TLS, uTLS fingerprints, and Flow support).
  - **Shadowsocks**.
  - Native **Hiddify / sing-box JSON config**.
- 🔀 **Split Tunneling:** Ability to proxy only selected applications by their `packageName`.
- 📊 **Real-time Monitoring:** Subscribe to connection status, upload/download speeds, and total traffic via `StateFlow`.
- 📝 **Thread-safe Logs:** System log journal for core and library logs with inter-process broadcasting support.
- 🔔 **Foreground Service Notifications:** Customizable title, text, and icon for the VPN notification.

---

## 🛠 Installation

### 1. Add JitPack to `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependency

Option A: Via Version Catalog (`gradle/libs.versions.toml`) — Recommended
```toml
[versions]
easyHiddify = "1.0.13" # Specify the latest version

[libraries]
easy-hiddify-lib = { group = "com.github.YaslikS", name = "easy_hiddify_lib", version.ref = "easyHiddify" }
```

In your app module's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.easy.hiddify.lib)
}
```

Option B: Directly in `build.gradle.kts`
```kotlin
dependencies {
    implementation("com.github.YaslikS:easy_hiddify_lib:1.0.13")
}
```

### 3. Configure `AndroidManifest.xml`
Add the required permissions and register the `HiddifyVpnService`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>

        <!-- Library VPN Service -->
        <service
            android:name="com.yasliks.hiddify_library_lib.service.HiddifyVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:foregroundServiceType="connectedDevice"
            android:exported="false">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

## 🚀 Quick Start
### 1. Library Initialization
Initialize `EasyHiddify` in your `Application` class:
```kotlin
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Library initialization
        EasyHiddify.init(this)
    }
}
```

### 2. Starting and Stopping VPN
```kotlin
val hiddify = EasyHiddify.instance

// Start VPN
fun connectVpn(configUrl: String) {
    hiddify.startVpn(
        configStr = configUrl, // VLESS link, SS link, or JSON
        serverName = "My Server", // Title in notification
        icon = R.drawable.ic_vpn_lock, // Custom notification icon (optional)
        appsList = listOf("com.android.chrome", "com.instagram.android"), // Split tunneling
        isEnabledApps = true // Enable app list
    )
}

// Stop VPN
fun disconnectVpn() {
    hiddify.stopVpn()
}
```

## 📊 State & Traffic Monitoring (`Jetpack Compose / Coroutines`)
You can subscribe to the VPN state from anywhere in your app:
```kotlin
@Composable
fun VpnScreen(hiddify: EasyHiddify = EasyHiddify.instance) {
    // Connection status (true / false)
    val isConnected by hiddify.state.connected.collectAsState()

    // Traffic and speed info (StatusMessage)
    val status by hiddify.state.status.collectAsState()

    // Real-time core and library logs
    val logs by hiddify.logger.logs.collectAsState()

    Column {
        Text(text = if (isConnected) "CONNECTED" else "DISCONNECTED")

        status?.let {
            // Format bytes using the built-in formatTraffic() extension
            Text("Download Speed: ${it.downlinkTotal.formatTraffic()}")
            Text("Upload Speed: ${it.uplinkTotal.formatTraffic()}")
        }
    }
}
```

## 💡 Supported Configuration Formats
The library automatically detects the format of the string passed to the `startVpn` method:

1. VLESS links: `vless://uuid@host:port?security=reality&pbk=...&fp=chrome#Name`
2. Shadowsocks links: `ss://base64(method:password)@host:port#Name`
3. JSON Hiddify Config: Raw valid JSON for the Hiddify/sing-box core (starting with `{`).

## ⚖️ License & Credits

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

This library includes binary components of [hiddify-core](https://github.com/hiddify/hiddify-core), which is licensed under the GPLv3.

### Third-Party Components:
- **Hiddify Core / Libbox** — [GPLv3 License](https://github.com/hiddify/hiddify-core)
- **sing-box** — [GPLv3 License](https://github.com/SagerNet/sing-box)

## ⚠️ Disclaimer

This library and sample application are provided **"AS IS"** for educational, research, and personal use only, without warranty of any kind.

The author assumes no responsibility or liability for how this software is used, including any misuse, law violations, network restrictions, or damages arising from the use of this library. Users are solely responsible for complying with all applicable local laws and regulations regarding VPN and network proxy usage.

> ⚠️ **Note for Developers:** Because this library depends on `hiddify-core` (GPLv3), any Android application that integrates `easy_hiddify_lib` must also comply with the GPLv3 license terms (i.e. make its source code open).