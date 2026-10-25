package com.yasliks.hiddify_library_lib.prefs

/**
 * Global configuration constants for the VPN library.
 * Contains Intent actions, SharedPreferences keys, default values, and general configuration.
 */
object HiddifyPrefs {
    const val HIDDIFY = "Hiddify"
    const val HIDDIFY_LIB = "HiddifyLib"
    const val HIDDIFY_SMALL = "hiddify"
    const val EASY_HIDDIFY_TEMP = "hiddify_temp"

    /** Supported VPN protocols. */
    const val VLESS_START_CONFIG = "vless"
    const val SHADOWSOCKS_START_CONFIG = "ss"
    const val TROJAN_START_CONFIG = "trojan"

    /** Intent extras for configuring the VPN service. */
    const val CONFIG_CONTENT = "config_content"
    const val NAME_SERVER = "name_server"
    const val ICON_PUSH = "icon_push"
    const val APPS_LIST = "apps_list"
    const val IS_ENABLED_APPS = "is_enabled_apps"
    const val DISCONNECT_TEXT = "disconnect_text"

    /** Intent actions and extras for VPN traffic broadcast. */
    const val ACTION_VPN_TRAFFIC = "com.yasliks.hiddify.VPN_TRAFFIC"
    const val EXTRA_UPLINK_SPEED = "extra_uplink_speed"
    const val EXTRA_DOWNLINK_SPEED = "extra_downlink_speed"
    const val EXTRA_UPLINK_TOTAL = "extra_uplink_total"
    const val EXTRA_DOWNLINK_TOTAL = "extra_downlink_total"

    /** Status update interval for the core client (in nanoseconds). */
    const val STATUS_INTERVAL = 1_000_000_000L

    const val VPN = "VPN"
    const val SERVER = "server"

    /** Default TUN interface values. */
    const val BASE_MTU = 1500
    const val BASE_DNS_SERVER = "8.8.8.8"
    const val BASE_ROUTE = "0.0.0.0"
    const val ZERO_ROUTE = "::"

    /** Intent actions for VPN state control. */
    const val ACTION_STOP_VPN = "STOP_VPN_ACTION"
    const val ACTION_VPN_STATE = "VPN_STATE_UPDATE"

    /** Intent actions and extras for logging. */
    const val ACTION_LOG_EVENT = "com.yasliks.hiddify.LOG_EVENT"
    const val ACTION_LOG_CLEAR = "com.yasliks.hiddify.LOG_CLEAR"
    const val EXTRA_LOG_LEVEL = "extra_log_level"
    const val EXTRA_LOG_MESSAGE = "extra_log_message"

    const val EXTRA_IS_CONNECTED = "extra_connected"
    const val COMMAND_SERVER_LISTEN_PORT = 6756
    const val DELAY_BEFORE_EXIT = 200L

    const val MAX_LOGS = 500
    const val CORE = "CORE"

    /** Notification channels and IDs. */
    const val CHANNEL_ID = "hiddify_lib_channel"
    const val NOTIFICATION_ID = 1001
    const val CONNECTED = "Connected"
    const val VPN_STATUS = "VPN Status"

    const val LENGH_RANDOM_HEX = 16

    const val ACTION_REQUEST_VPN_STATE = "com.yasliks.hiddify.REQUEST_VPN_STATE"

    /** Base SharedPreferences for VPN state. */
    const val EXTRA_SERVER_ID = "extra_server_id"
    const val EXTRA_SERVER_NAME = "extra_server_name"
    const val PREFS_NAME = "hiddify_vpn_state"
    const val KEY_IS_RUNNING = "is_running"
    const val KEY_SERVER_ID = "server_id"
    const val KEY_SERVER_NAME = "server_name"

    /** SharedPreferences name for user settings. */
    const val SETTINGS_PREFS_NAME = "hiddify_settings_prefs"

    /** Keys for user settings. */
    const val KEY_DNS_SERVER = "settings_dns_server"
    const val KEY_MTU = "settings_mtu"
    const val KEY_ENABLE_IPV6 = "settings_enable_ipv6"
    const val KEY_BYPASS_LAN = "settings_bypass_lan"
    const val KEY_LOG_LEVEL = "settings_log_level"

    const val KEY_STRICT_ROUTE = "settings_strict_route"
    const val KEY_ENABLE_MUX = "settings_enable_mux"
    const val KEY_ENABLE_LOCAL_PROXY = "settings_enable_local_proxy"

    const val KEY_ENABLE_ADBLOCK = "settings_enable_adblock"
    const val KEY_CUSTOM_DIRECT_DOMAINS = "settings_custom_direct_domains"
    const val KEY_ENABLE_FAKE_IP = "settings_enable_fake_ip"

    /** Default user settings values. */
    const val DEFAULT_DNS = "8.8.8.8"
    const val DEFAULT_MTU = 1500
    const val DEFAULT_LOG_LEVEL = "warn"
}