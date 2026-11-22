package com.yasliks.hiddify_library_lib.utils

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import com.hiddify.core.libbox.CommandClientHandler
import com.hiddify.core.libbox.CommandClientOptions
import com.hiddify.core.libbox.ConnectionEvents
import com.hiddify.core.libbox.Libbox
import com.hiddify.core.libbox.LogIterator
import com.hiddify.core.libbox.OutboundGroupItemIterator
import com.hiddify.core.libbox.OutboundGroupIterator
import com.hiddify.core.libbox.OverrideOptions
import com.hiddify.core.libbox.SetupOptions
import com.hiddify.core.libbox.StatusMessage
import com.hiddify.core.libbox.StringIterator
import com.yasliks.hiddify_library_lib.core.HiddifyCommandHandler
import com.yasliks.hiddify_library_lib.core.HiddifyPlatform
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createTempDirectory

sealed interface LatencyResult {
    data class Reachable(val milliseconds: Int) : LatencyResult
    data object NoResponse : LatencyResult
    data object Failed : LatencyResult
}

class HiddifyLatencyTest internal constructor(
    private val context: Context,
    private val generator: HiddifyConfigUtils,
) {
    private val lock = Mutex()

    suspend fun measure(config: String): LatencyResult {
        var result: LatencyResult = LatencyResult.Failed
        measureBatch(
            configs = listOf(element = config),
            onResult = { _, value -> result = value },
        )
        return result
    }

    suspend fun measureBatch(
        configs: List<String>,
        onResult: suspend (index: Int, result: LatencyResult) -> Unit,
    ) {
        require(configs.size <= MAX_BATCH_SIZE)
        require(configs.size <= 1 || configs.none { it.trimStart().startsWith(char = '{') })
        if (configs.isEmpty()) return
        probeResults(configs = configs).collect { (index, result) ->
            onResult(index, result)
        }
    }

    private fun probeResults(configs: List<String>) = channelFlow {
        val reported = ConcurrentHashMap.newKeySet<Int>()
        suspend fun report(
            index: Int,
            result: LatencyResult,
        ) {
            if (reported.add(index)) send(element = index to result)
        }
        lock.withLock {
            try {
                check(isMainProcess())
                val probe = if (configs.size == 1) {
                    singleProbe(config = configs.single())
                } else {
                    batchProbe(configs = configs)
                }
                configs.indices.filterNot { it in probe.targets.values }.forEach {
                    report(
                        index = it,
                        result = LatencyResult.Failed,
                    )
                }
                runProbe(
                    probe = probe,
                    onResult = ::report,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                configs.indices.forEach {
                    report(
                        index = it,
                        result = LatencyResult.Failed,
                    )
                }
            }
        }
    }.flowOn(context = Dispatchers.IO)

    private data class Probe(
        val config: JSONObject,
        val targets: Map<String, Int>,
    )

    private fun singleProbe(config: String): Probe {
        val generated = JSONObject(generator.generateConfig(configStr = config))
        val outbounds = generated.getJSONArray("outbounds")
        val originalRoute = generated.optJSONObject("route")
        val target = originalRoute?.optString("final")?.takeIf { it.isNotBlank() }
            ?: outbounds.optJSONObject(0)?.optString("tag").orEmpty()
        require(target.isNotBlank())
        val probeOutbounds = JSONArray()
        var targetSupported = false
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.getJSONObject(index)
            require(outbound.optString("tag") != PROBE_TAG)
            val type = outbound.optString("type")
            if (outbound.optString("tag") == target && type in SUPPORTED_TYPES) targetSupported = true
            if (type in SUPPORTED_TYPES || type == "direct") probeOutbounds.put(outbound)
        }
        require(targetSupported)
        addProbeGroup(
            outbounds = probeOutbounds,
            targets = listOf(element = target),
        )
        val probeConfig = JSONObject().apply {
            put("log", JSONObject().put("disabled", true))
            generated.optJSONObject("dns")?.let { put("dns", it) }
            put("outbounds", probeOutbounds)
            put("route", JSONObject().apply {
                put("final", target)
                originalRoute?.opt("default_domain_resolver")?.let { put("default_domain_resolver", it) }
            })
        }
        return Probe(
            config = probeConfig,
            targets = mapOf(pair = target to 0),
        )
    }

    private fun batchProbe(configs: List<String>): Probe {
        val outbounds = JSONArray()
        val targets = linkedMapOf<String, Int>()
        var resolver: JSONObject? = null
        for ((index, config) in configs.withIndex()) {
            val generated = runCatching {
                JSONObject(generator.generateConfig(configStr = config))
            }.getOrNull() ?: continue
            val options = generated.optJSONArray("outbounds") ?: continue
            val proxy = options.optJSONObject(0)?.takeIf { it.optString("type") in SUPPORTED_TYPES } ?: continue
            val tag = "probe-$index"
            outbounds.put(proxy.put("tag", tag))
            targets[tag] = index
            if (resolver == null) {
                val servers = generated.optJSONObject("dns")?.optJSONArray("servers")
                resolver = (0 until (servers?.length() ?: 0))
                    .mapNotNull { servers?.optJSONObject(it) }
                    .firstOrNull { it.optString("tag") == "dns-direct" }
            }
        }
        require(targets.isNotEmpty())
        addProbeGroup(
            outbounds = outbounds,
            targets = targets.keys.toList(),
        )
        return Probe(
            config = JSONObject().apply {
                put("log", JSONObject().put("disabled", true))
                put("outbounds", outbounds)
                put("dns", JSONObject().put("servers", JSONArray().put(
                    resolver ?: JSONObject().put("type", "local").put("tag", "dns-direct"),
                )))
                put("route", JSONObject().put("final", PROBE_TAG).put("default_domain_resolver", "dns-direct"))
            },
            targets = targets,
        )
    }

    private fun addProbeGroup(
        outbounds: JSONArray,
        targets: List<String>,
    ) {
        outbounds.put(JSONObject().apply {
            put("type", "urltest")
            put("tag", PROBE_TAG)
            put("outbounds", JSONArray(targets))
            put("url", "https://www.gstatic.com/generate_204")
        })
    }

    private suspend fun runProbe(
        probe: Probe,
        onResult: suspend (index: Int, result: LatencyResult) -> Unit,
    ) {
        val network = checkNotNull(findPhysicalNetwork())
        val dir = createTempDirectory(
            directory = context.cacheDir.toPath(),
            prefix = "lt",
        ).toFile()
        try {
            Libbox.setup(SetupOptions().apply {
                basePath = dir.absolutePath
                workingPath = dir.absolutePath
                tempPath = dir.absolutePath
                commandServerListenPort = 0
                fixAndroidStack = true
            })
            val server = Libbox.newCommandServer(
                HiddifyCommandHandler(context = context),
                HiddifyPlatform(
                    context = context,
                    probeNetwork = network,
                ),
            )
            try {
                server.start()
                server.startOrReloadService(
                    probe.config.toString(),
                    OverrideOptions(),
                )
                val results = probe.targets.mapValues { CompletableDeferred<LatencyResult>() }
                val client = Libbox.newCommandClient(
                    ProbeHandler(results = results),
                    CommandClientOptions().apply { addCommand(Libbox.CommandGroup) },
                )
                try {
                    client.connect()
                    client.urlTest(PROBE_TAG)
                    coroutineScope {
                        for ((tag, result) in results) launch {
                            onResult(
                                probe.targets.getValue(tag),
                                withTimeoutOrNull(timeMillis = PROBE_TIMEOUT_MS) { result.await() }
                                    ?: LatencyResult.NoResponse,
                            )
                        }
                    }
                } finally {
                    runCatching { client.disconnect() }
                }
            } finally {
                runCatching { server.closeService() }
                server.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun findPhysicalNetwork(): Network? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return (listOfNotNull(connectivity.activeNetwork) + connectivity.allNetworks).firstOrNull {
            val capabilities = connectivity.getNetworkCapabilities(it)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private fun isMainProcess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName() == context.applicationInfo.processName
        } else {
            context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.any { it.pid == Process.myPid() && it.processName == context.applicationInfo.processName } == true
        }

    private class ProbeHandler(
        private val results: Map<String, CompletableDeferred<LatencyResult>>,
    ) : CommandClientHandler {
        override fun writeGroups(groups: OutboundGroupIterator) {
            while (groups.hasNext()) {
                val group = groups.next()
                if (group.tag != PROBE_TAG) continue
                val items = group.items ?: continue
                while (items.hasNext()) {
                    val item = items.next()
                    if (item.urlTestTime > 0) {
                        results[item.tag]?.complete(LatencyResult.Reachable(milliseconds = item.urlTestDelay))
                    }
                }
            }
        }

        override fun disconnected(message: String?) {
            results.values.forEach { it.complete(LatencyResult.Failed) }
        }
        override fun connected() {}
        override fun clearLogs() {}
        override fun initializeClashMode(
            modeList: StringIterator?,
            currentMode: String?,
        ) {}
        override fun setDefaultLogLevel(level: Int) {}
        override fun updateClashMode(newMode: String?) {}
        override fun writeConnectionEvents(events: ConnectionEvents?) {}
        override fun writeLogs(logs: LogIterator?) {}
        override fun writeOutbounds(outbounds: OutboundGroupItemIterator?) {}
        override fun writeStatus(status: StatusMessage?) {}
    }

    companion object {
        const val MAX_BATCH_SIZE = 10
        private const val PROBE_TAG = "easy-latency-test"
        private const val PROBE_TIMEOUT_MS = 3_000L
        private val SUPPORTED_TYPES = setOf(
            HiddifyPrefs.VLESS_START_CONFIG,
            HiddifyPrefs.TROJAN_START_CONFIG,
            "shadowsocks",
            HiddifyPrefs.HYSTERIA2_START_CONFIG,
        )
    }
}
