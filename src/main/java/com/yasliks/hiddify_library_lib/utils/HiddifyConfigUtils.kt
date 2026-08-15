package com.yasliks.hiddify_library_lib.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import com.yasliks.easy_hiddify_lib.R
import com.yasliks.hiddify_library_lib.EasyHiddify
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs.SHADOWSOCKS_START_CONFIG
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs.VLESS_START_CONFIG
import org.json.JSONArray
import org.json.JSONObject

/**
 * Config parsing utility
 *
 * Parses configs `vless://` and `ss://`
 */
class HiddifyConfigUtils(private val context: Context) {


    private val sdk get() = EasyHiddify.instance


    /**
     * Parses configs
     *
     * @param configStr configuration in the format `vless://`, `ss://` or `hiddify`
     *
     * @return returns the config in the format `hiddify (json)`
     */
    fun generateConfig(configStr: String): String {
        val trimmed = configStr.trim()
        sdk.logger.append(2, "[CONFIG] Generating config from string input...")

        if (trimmed.startsWith("{")) {
            sdk.logger.append(2, "[CONFIG] Raw JSON config detected")
            return trimmed
        }

        try {
            val uri = trimmed.toUri()
            sdk.logger.append(2, "[CONFIG] Protocol scheme detected: ${uri.scheme}")
            return when (uri.scheme) {
                VLESS_START_CONFIG -> generateVless(uri)
                SHADOWSOCKS_START_CONFIG -> generateShadowsocks(uri)
                else -> {
                    val err = context.getString(R.string.unsupported_protocol, uri.scheme)
                    sdk.logger.append(4, "[CONFIG ERROR] $err")
                    throw Exception(err)
                }
            }
        } catch (e: Exception) {
            val errMessage = e.message ?: "Unknown config parsing error"
            sdk.logger.append(4, "[CONFIG ERROR] $errMessage")
            return JSONObject().put(context.getString(R.string.error), errMessage).toString()
        }
    }

    private fun generateVless(uri: Uri): String {
        val uuid = uri.userInfo
        val host = uri.host ?: ""
        val port = uri.port
        val security = uri.getQueryParameter("security") ?: "none"
        val sni = uri.getQueryParameter("sni") ?: ""
        val fp = uri.getQueryParameter("fp") ?: "chrome"
        val flow = uri.getQueryParameter("flow") ?: ""

        // Parameters for Reality
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""

        sdk.logger.append(2, "[CONFIG] Parsed VLESS -> Host: $host:$port | Security: $security | SNI: $sni | FP: $fp | Flow: $flow")

        val root = JSONObject()

        root.put("log", JSONObject().apply {
            put("level", "debug")
            put("timestamp", true)
        })

        root.put(
            "dns", JSONObject().put(
                "servers", JSONArray().put(
                    JSONObject().apply {
                        put("tag", "dns-remote")
                        put("address", "8.8.8.8")
                    }
                )))

        // Inbounds (TUN)
        root.put("inbounds", JSONArray().put(JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("address", JSONArray().put("172.19.0.1/30"))
            put("auto_route", true)
            put("strict_route", true)
            put("stack", "gvisor")
            put("sniff", true)
        }))

        // Outbounds
        val outbounds = JSONArray()

        // Main proxy (VLESS)
        val proxy = JSONObject().apply {
            put("type", VLESS_START_CONFIG)
            put("tag", "proxy")
            put(HiddifyPrefs.SERVER, host)
            put("server_port", port)
            put("uuid", uuid)
            if (flow.isNotEmpty()) put("flow", flow)

            // Configuring TLS / Reality
            if (security == "reality" || security == "tls" || security == "xtls") {
                val tls = JSONObject()
                tls.put("enabled", true)
                tls.put("server_name", sni)

                // Fingerprint (uTLS)
                tls.put("utls", JSONObject().apply {
                    put("enabled", true)
                    put("fingerprint", fp)
                })

                // Если это Reality, добавляем специфичный блок
                if (security == "reality") {
                    tls.put("reality", JSONObject().apply {
                        put("enabled", true)
                        put("public_key", pbk)
                        put("short_id", sid)
                    })
                }

                put("tls", tls)
            }
        }

        outbounds.put(proxy)
        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
        outbounds.put(JSONObject().put("type", "dns").put("tag", "dns-out"))

        root.put("outbounds", outbounds)

        // Route
        root.put("route", JSONObject().apply {
            put("rules", JSONArray().put(JSONObject().apply {
                put("protocol", "dns")
                put("outbound", "dns-out")
            }))
            put("final", "proxy")
        })

        val res = root.toString(2)
        return res
    }

    private fun generateShadowsocks(uri: Uri): String {
        val host = uri.host ?: ""
        val port = uri.port

        // Decoding the userInfo (method:password) from Base64
        val decodedUserInfo = try {
            String(Base64.decode(uri.userInfo ?: "", Base64.DEFAULT))
        } catch (e: Exception) {
            val err = context.getString(R.string.invalid_ss_base)
            sdk.logger.append(4, "[CONFIG ERROR] $err")
            throw Exception(err)
        }

        val parts = decodedUserInfo.split(":", limit = 2)
        if (parts.size < 2) {
            val err = context.getString(R.string.invalid_ss_userinfo_format)
            sdk.logger.append(4, "[CONFIG ERROR] $err")
            throw Exception(err)
        }

        val method = parts[0]
        val password = parts[1]

        sdk.logger.append(2, "[CONFIG] Parsed SS -> Host: $host:$port | Method: $method")

        val root = JSONObject()

        root.put("log", JSONObject().apply {
            put("level", "debug")
            put("timestamp", true)
        })

        // DNS
        root.put(
            "dns", JSONObject().put(
                "servers", JSONArray().put(
                    JSONObject().apply {
                        put("tag", "dns-remote")
                        put("address", "8.8.8.8")
                    }
                )))

        // Inbounds (TUN)
        root.put("inbounds", JSONArray().put(JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("address", JSONArray().put("172.19.0.1/30"))
            put("auto_route", true)
            put("strict_route", false)
            put("stack", "gvisor")
            put("sniff", true)
        }))

        // Outbounds
        val outbounds = JSONArray()
        val proxy = JSONObject().apply {
            put("type", "shadowsocks")
            put("tag", "proxy")
            put(HiddifyPrefs.SERVER, host)
            put("server_port", port)
            put("method", method)
            put("password", password)
        }

        outbounds.put(proxy)
        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
        outbounds.put(JSONObject().put("type", "dns").put("tag", "dns-out"))
        root.put("outbounds", outbounds)

        // Route
        root.put("route", JSONObject().apply {
            put("rules", JSONArray().put(JSONObject().apply {
                put("protocol", "dns")
                put("outbound", "dns-out")
            }))
            put("final", "proxy")
        })

        val res = root.toString(2)
        return res
    }
}