package com.yasliks.hiddify_library_lib.prefs

object HiddifyPrefs {
    const val HIDDIFY = "Hiddify"
    const val HIDDIFY_LIB = "HiddifyLib"
    const val HIDDIFY_SMALL = "hiddify"
    const val EASY_HIDDIFY_TEMP = "hiddify_temp"

    const val VLESS_START_CONFIG = "vless"
    const val SHADOWSOCKS_START_CONFIG = "ss"

    const val CONFIG_CONTENT = "config_content"
    const val NAME_SERVER = "name_server"
    const val ICON_PUSH = "icon_push"
    const val APPS_LIST = "apps_list"
    const val IS_ENABLED_APPS = "is_enabled_apps"

    const val VPN = "VPN"
    const val SERVER = "server"

    const val BASE_MTU = 1500
    const val BASE_DNS_SERVER = "8.8.8.8"
    const val BASE_ROUTE = "0.0.0.0"
    const val ZERO_ROUTE = "::"

    const val ACTION_STOP_VPN = "STOP_VPN_ACTION"
    const val ACTION_VPN_STATE = "VPN_STATE_UPDATE"

    const val ACTION_LOG_EVENT = "com.yasliks.hiddify.LOG_EVENT"
    const val ACTION_LOG_CLEAR = "com.yasliks.hiddify.LOG_CLEAR"
    const val EXTRA_LOG_LEVEL = "extra_log_level"
    const val EXTRA_LOG_MESSAGE = "extra_log_message"

    const val EXTRA_IS_CONNECTED = "extra_connected"
    const val COMMAND_SERVER_LISTEN_PORT = 6756
    const val STATUS_INTERVAL = 1000L
    const val DELAY_BEFORE_EXIT = 200L

    const val MAX_LOGS = 500
    const val CORE = "CORE"

    const val CHANNEL_ID = "hiddify_lib_channel"
    const val NOTIFICATION_ID = 1001
    const val CONNECTED = "Connected"
    const val VPN_STATUS = "VPN Status"

    const val LENGH_RANDOM_HEX = 16
}