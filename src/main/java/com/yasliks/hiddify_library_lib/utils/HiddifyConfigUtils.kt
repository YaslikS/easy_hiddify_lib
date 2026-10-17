package com.yasliks.hiddify_library_lib.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import com.yasliks.easy_hiddify_lib.R
import com.yasliks.hiddify_library_lib.EasyHiddify
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs.SHADOWSOCKS_START_CONFIG
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs.TROJAN_START_CONFIG
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs.VLESS_START_CONFIG
import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuration parsing utility.
 *
 * This class is responsible for converting standard VPN URIs (`vless://`, `trojan://`, `ss://`)
 * or raw JSON files into a format that the Sing-box core understands.
 * It injects user-defined settings dynamically (e.g., DNS, Multiplexing, AdBlock, Local Proxy)
 * during the configuration build process.
 */
class HiddifyConfigUtils(private val context: Context) {

    private val sdk get() = EasyHiddify.instance

    /**
     * Main entry point to parse a raw configuration string.
     *
     * @param configStr The VPN configuration string in URI format or raw Sing-box JSON.
     * @return A complete, formatted Sing-box JSON configuration string.
     */
    fun generateConfig(configStr: String): String {
        val trimmed = configStr.trim()
        sdk.logger.append(2, "[CONFIG] Generating config from string input...")

        // Return immediately if it's already a raw JSON config
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
                TROJAN_START_CONFIG -> generateTrojan(uri)
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

    /**
     * Generates a Sing-box JSON configuration for VLESS protocol.
     */
    private fun generateVless(uri: Uri): String {
        val uuid = uri.userInfo
        val host = uri.host ?: ""
        val port = uri.port
        val security = uri.getQueryParameter("security") ?: "none"
        val sni = uri.getQueryParameter("sni") ?: ""
        val fp = uri.getQueryParameter("fp") ?: "chrome"
        val flow = uri.getQueryParameter("flow") ?: ""
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""

        val root = JSONObject()
        root.put("log", buildLogObject())
        root.put("dns", buildDnsObject())
        root.put("inbounds", buildInboundsObject())

        val outbounds = JSONArray()
        val proxy = JSONObject().apply {
            put("type", VLESS_START_CONFIG)
            put("tag", "proxy")
            put(HiddifyPrefs.SERVER, host)
            put("server_port", port)
            put("uuid", uuid)
            if (flow.isNotEmpty()) put("flow", flow)

            if (sdk.settings.enableMux.value) {
                put("multiplex", JSONObject().apply {
                    put("enabled", true)
                    put("protocol", "h2mux")
                    put("max_connections", 4)
                })
            }

            if (security == "reality" || security == "tls" || security == "xtls") {
                val tls = JSONObject()
                tls.put("enabled", true)
                tls.put("server_name", sni)
                tls.put("utls", JSONObject().apply {
                    put("enabled", true)
                    put("fingerprint", fp)
                })
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

        buildOutbounds(outbounds, proxy)
        root.put("outbounds", outbounds)
        root.put("route", buildRouteObject())

        return root.toString(2)
    }

    /**
     * Generates a Sing-box JSON configuration for Trojan protocol.
     */
    private fun generateTrojan(uri: Uri): String {
        val password = uri.userInfo ?: ""
        val host = uri.host ?: ""
        val port = uri.port
        val security = uri.getQueryParameter("security") ?: "tls"
        val sni = uri.getQueryParameter("sni") ?: ""
        val fp = uri.getQueryParameter("fp") ?: "chrome"
        val pbk = uri.getQueryParameter("pbk") ?: ""
        val sid = uri.getQueryParameter("sid") ?: ""
        val type = uri.getQueryParameter("type") ?: "tcp"
        val path = uri.getQueryParameter("path") ?: ""
        val hostHeader = uri.getQueryParameter("host") ?: ""
        val serviceName = uri.getQueryParameter("serviceName") ?: ""

        val root = JSONObject()
        root.put("log", buildLogObject())
        root.put("dns", buildDnsObject())
        root.put("inbounds", buildInboundsObject())

        val outbounds = JSONArray()
        val proxy = JSONObject().apply {
            put("type", "trojan")
            put("tag", "proxy")
            put("server", host)
            put("server_port", port)
            put("password", password)

            if (sdk.settings.enableMux.value) {
                put("multiplex", JSONObject().apply {
                    put("enabled", true)
                    put("protocol", "h2mux")
                    put("max_connections", 4)
                })
            }

            val serverName = if (sni.isNotEmpty()) sni else host

            if (security == "reality" || security == "tls" || security == "xtls" || security.isEmpty()) {
                val tls = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", serverName)
                    put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", if (fp.isNotEmpty()) fp else "chrome")
                    })
                    if (security == "reality") {
                        put("reality", JSONObject().apply {
                            put("enabled", true)
                            put("public_key", pbk)
                            put("short_id", sid)
                        })
                    }
                }
                put("tls", tls)
            }

            if (type == "ws") {
                put("transport", JSONObject().apply {
                    put("type", "ws")
                    if (path.isNotEmpty()) put("path", path)
                    if (hostHeader.isNotEmpty()) put("headers", JSONObject().put("Host", hostHeader))
                })
            } else if (type == "grpc") {
                put("transport", JSONObject().apply {
                    put("type", "grpc")
                    if (serviceName.isNotEmpty()) put("service_name", serviceName)
                })
            } else if (type == "httpupgrade") {
                put("transport", JSONObject().apply {
                    put("type", "httpupgrade")
                    if (path.isNotEmpty()) put("path", path)
                    if (hostHeader.isNotEmpty()) put("host", hostHeader)
                })
            }
        }

        buildOutbounds(outbounds, proxy)
        root.put("outbounds", outbounds)
        root.put("route", buildRouteObject())

        return root.toString(2)
    }

    /**
     * Generates a Sing-box JSON configuration for Shadowsocks protocol.
     */
    private fun generateShadowsocks(uri: Uri): String {
        val host = uri.host ?: ""
        val port = uri.port
        val decodedUserInfo = try {
            String(Base64.decode(uri.userInfo ?: "", Base64.DEFAULT))
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.invalid_ss_base))
        }

        val parts = decodedUserInfo.split(":", limit = 2)
        if (parts.size < 2) throw Exception(context.getString(R.string.invalid_ss_userinfo_format))

        val method = parts[0]
        val password = parts[1]

        val root = JSONObject()
        root.put("log", buildLogObject())
        root.put("dns", buildDnsObject())
        root.put("inbounds", buildInboundsObject())

        val outbounds = JSONArray()
        val proxy = JSONObject().apply {
            put("type", "shadowsocks")
            put("tag", "proxy")
            put(HiddifyPrefs.SERVER, host)
            put("server_port", port)
            put("method", method)
            put("password", password)

            if (sdk.settings.enableMux.value) {
                put("multiplex", JSONObject().apply {
                    put("enabled", true)
                    put("protocol", "h2mux")
                    put("max_connections", 4)
                })
            }
        }

        buildOutbounds(outbounds, proxy)
        root.put("outbounds", outbounds)
        root.put("route", buildRouteObject())

        return root.toString(2)
    }

    /**
     * Builds the logging configuration block.
     * Incorporates the log level from user settings.
     */
    private fun buildLogObject(): JSONObject {
        return JSONObject().apply {
            put("level", sdk.settings.logLevel.value)
            put("timestamp", true)
        }
    }

    /**
     * Builds the DNS configuration block.
     * Incorporates custom DNS server and Fake IP capabilities based on user settings.
     */
    private fun buildDnsObject(): JSONObject {
        val dnsObject = JSONObject().apply {
            put("servers", JSONArray().put(
                JSONObject().apply {
                    put("tag", "dns-remote")
                    put("address", sdk.settings.dnsServer.value)
                }
            ))

            if (sdk.settings.enableFakeIp.value) {
                put("fakeip", JSONObject().apply {
                    put("enabled", true)
                    put("inet4_range", "198.18.0.0/15")
                    put("inet6_range", "fc00::/18")
                })
            }
        }
        return dnsObject
    }

    /**
     * Builds the inbound connections block.
     * Defines the TUN interface for the device and an optional local proxy port
     * (LAN Share) if enabled in settings.
     */
    private fun buildInboundsObject(): JSONArray {
        val inbounds = JSONArray()

        inbounds.put(JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("address", JSONArray().put("172.19.0.1/30"))
            put("auto_route", true)
            put("strict_route", sdk.settings.strictRoute.value)
            put("stack", "gvisor")
            put("sniff", true)
        })

        if (sdk.settings.enableLocalProxy.value) {
            inbounds.put(JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "0.0.0.0")
                put("listen_port", 10808)
                put("sniff", true)
            })
        }

        return inbounds
    }

    /**
     * Constructs the outbound structure.
     * Adds the primary proxy connection, direct, DNS, and an optional block outbound for AdBlock.
     */
    private fun buildOutbounds(outbounds: JSONArray, proxy: JSONObject) {
        outbounds.put(proxy)
        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
        outbounds.put(JSONObject().put("type", "dns").put("tag", "dns-out"))

        if (sdk.settings.enableAdBlock.value) {
            outbounds.put(JSONObject().put("type", "block").put("tag", "block"))
        }
    }

    /**
     * Builds the routing rules block.
     * Integrates AdBlock categories, custom direct domain rules, and Bypass LAN rules.
     */
    private fun buildRouteObject(): JSONObject {
        val routeRules = JSONArray()

        if (sdk.settings.enableAdBlock.value) {
            routeRules.put(JSONObject().apply {
                put("geosite", JSONArray().put("category-ads-all"))
                put("outbound", "block")
            })
        }

        val customDomainsStr = sdk.settings.customDirectDomains.value
        if (customDomainsStr.isNotBlank()) {
            val domainsList = customDomainsStr.split(",")
                .map { it.trim().removePrefix("*.") }
                .filter { it.isNotEmpty() }

            if (domainsList.isNotEmpty()) {
                routeRules.put(JSONObject().apply {
                    put("domain_suffix", JSONArray(domainsList))
                    put("outbound", "direct")
                })
            }
        }

        if (sdk.settings.bypassLan.value) {
            routeRules.put(JSONObject().apply {
                put("outbound", "direct")
                put("ip_cidr", JSONArray().apply {
                    put("192.168.0.0/16")
                    put("10.0.0.0/8")
                    put("172.16.0.0/12")
                    put("fc00::/7")
                    put("fe80::/10")
                })
            })
        }

        routeRules.put(JSONObject().apply {
            put("protocol", "dns")
            put("outbound", "dns-out")
        })

        return JSONObject().apply {
            put("rules", routeRules)
            put("final", "proxy")
        }
    }
}