package com.yasliks.hiddify_library_lib.utils

import android.content.Context
import androidx.core.content.edit
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Utility class to manage and observe VPN user settings.
 * Saves preferences locally via SharedPreferences and exposes them as [StateFlow]
 * for reactive UI updates in Jetpack Compose or ViewModels.
 */
class HiddifySettingsUtils(context: Context) {

    private val prefs = context.getSharedPreferences(
        HiddifyPrefs.SETTINGS_PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    /**
     * Custom DNS server address.
     * Overrides the default DNS used by the core (e.g., 8.8.8.8, 1.1.1.1).
     */
    private val _dnsServer = MutableStateFlow(
        prefs.getString(HiddifyPrefs.KEY_DNS_SERVER, HiddifyPrefs.DEFAULT_DNS) ?: HiddifyPrefs.DEFAULT_DNS
    )
    val dnsServer = _dnsServer.asStateFlow()

    fun setDnsServer(dns: String) {
        prefs.edit { putString(HiddifyPrefs.KEY_DNS_SERVER, dns) }
        _dnsServer.value = dns
    }

    /**
     * Maximum Transmission Unit (MTU) size.
     * Defines the maximum packet size for the TUN interface.
     */
    private val _mtu = MutableStateFlow(
        prefs.getInt(HiddifyPrefs.KEY_MTU, HiddifyPrefs.DEFAULT_MTU)
    )
    val mtu = _mtu.asStateFlow()

    fun setMtu(mtuValue: Int) {
        prefs.edit { putInt(HiddifyPrefs.KEY_MTU, mtuValue) }
        _mtu.value = mtuValue
    }

    /**
     * Toggles IPv6 routing support.
     * When disabled, prevents IPv6 address leaks by forcing traffic over IPv4.
     */
    private val _enableIPv6 = MutableStateFlow(
        prefs.getBoolean(HiddifyPrefs.KEY_ENABLE_IPV6, true)
    )
    val enableIPv6 = _enableIPv6.asStateFlow()

    fun setEnableIPv6(enable: Boolean) {
        prefs.edit { putBoolean(HiddifyPrefs.KEY_ENABLE_IPV6, enable) }
        _enableIPv6.value = enable
    }

    /**
     * Bypass Local Area Network.
     * When enabled, allows the device to communicate directly with local IP addresses
     * (e.g., printers, smart home) instead of routing them through the VPN.
     */
    private val _bypassLan = MutableStateFlow(
        prefs.getBoolean(HiddifyPrefs.KEY_BYPASS_LAN, true)
    )
    val bypassLan = _bypassLan.asStateFlow()

    fun setBypassLan(bypass: Boolean) {
        prefs.edit { putBoolean(HiddifyPrefs.KEY_BYPASS_LAN, bypass) }
        _bypassLan.value = bypass
    }

    /**
     * Core logging level.
     * Used for debugging or battery saving. Values: trace, debug, info, warn, error, fatal.
     */
    private val _logLevel = MutableStateFlow(
        prefs.getString(HiddifyPrefs.KEY_LOG_LEVEL, HiddifyPrefs.DEFAULT_LOG_LEVEL) ?: HiddifyPrefs.DEFAULT_LOG_LEVEL
    )
    val logLevel = _logLevel.asStateFlow()

    fun setLogLevel(level: String) {
        prefs.edit { putString(HiddifyPrefs.KEY_LOG_LEVEL, level) }
        _logLevel.value = level
    }

    /**
     * Strict Route mode.
     * Prevents traffic leaks when the network interface changes (e.g., from Wi-Fi to Cellular).
     */
    private val _strictRoute = MutableStateFlow(
        prefs.getBoolean(HiddifyPrefs.KEY_STRICT_ROUTE, true)
    )
    val strictRoute = _strictRoute.asStateFlow()

    fun setStrictRoute(enable: Boolean) {
        prefs.edit { putBoolean(HiddifyPrefs.KEY_STRICT_ROUTE, enable) }
        _strictRoute.value = enable
    }

    /**
     * Multiplexing (Mux).
     * Combines multiple TCP connections into a single proxy connection.
     * Reduces connection setup latency and saves battery, but may reduce peak throughput.
     */
    private val _enableMux = MutableStateFlow(
        prefs.getBoolean(HiddifyPrefs.KEY_ENABLE_MUX, false)
    )
    val enableMux = _enableMux.asStateFlow()

    fun setEnableMux(enable: Boolean) {
        prefs.edit { putBoolean(HiddifyPrefs.KEY_ENABLE_MUX, enable) }
        _enableMux.value = enable
    }

    /**
     * Local Proxy (LAN Share).
     * Opens an HTTP/SOCKS5 proxy port (default: 10808) on the device,
     * allowing other devices on the same Wi-Fi network to route their traffic through this VPN.
     */
    private val _enableLocalProxy = MutableStateFlow(
        prefs.getBoolean(HiddifyPrefs.KEY_ENABLE_LOCAL_PROXY, false)
    )
    val enableLocalProxy = _enableLocalProxy.asStateFlow()

    fun setEnableLocalProxy(enable: Boolean) {
        prefs.edit { putBoolean(HiddifyPrefs.KEY_ENABLE_LOCAL_PROXY, enable) }
        _enableLocalProxy.value = enable
    }

    /**
     * AdBlock via geosite.
     * Blocks ads and trackers directly at the DNS level using core geosite capabilities.
     */
    private val _enableAdBlock = MutableStateFlow(
        prefs.getBoolean(HiddifyPrefs.KEY_ENABLE_ADBLOCK, false)
    )
    val enableAdBlock = _enableAdBlock.asStateFlow()

    fun setEnableAdBlock(enable: Boolean) {
        prefs.edit { putBoolean(HiddifyPrefs.KEY_ENABLE_ADBLOCK, enable) }
        _enableAdBlock.value = enable
    }

    /**
     * Custom split tunneling domains.
     * A comma-separated list of domains that will always bypass the VPN (direct routing).
     */
    private val _customDirectDomains = MutableStateFlow(
        prefs.getString(HiddifyPrefs.KEY_CUSTOM_DIRECT_DOMAINS, "") ?: ""
    )
    val customDirectDomains = _customDirectDomains.asStateFlow()

    fun setCustomDirectDomains(domains: String) {
        prefs.edit { putString(HiddifyPrefs.KEY_CUSTOM_DIRECT_DOMAINS, domains) }
        _customDirectDomains.value = domains
    }

    /**
     * Fake IP mode.
     * Accelerates DNS resolution by returning a dummy IP instantly and resolving the actual
     * address on the remote proxy server. Highly improves browsing speed.
     */
    private val _enableFakeIp = MutableStateFlow(
        prefs.getBoolean(HiddifyPrefs.KEY_ENABLE_FAKE_IP, false)
    )
    val enableFakeIp = _enableFakeIp.asStateFlow()

    fun setEnableFakeIp(enable: Boolean) {
        prefs.edit { putBoolean(HiddifyPrefs.KEY_ENABLE_FAKE_IP, enable) }
        _enableFakeIp.value = enable
    }
}