package com.yasliks.hiddify_library_lib.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.hiddify.core.libbox.CommandClient
import com.hiddify.core.libbox.CommandClientOptions
import com.hiddify.core.libbox.CommandServer
import com.hiddify.core.libbox.Libbox
import com.hiddify.core.libbox.OverrideOptions
import com.hiddify.core.libbox.SetupOptions
import com.yasliks.hiddify_library_lib.EasyHiddify
import com.yasliks.hiddify_library_lib.core.HiddifyClientHandler
import com.yasliks.hiddify_library_lib.core.HiddifyCommandHandler
import com.yasliks.hiddify_library_lib.core.HiddifyPlatform
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import androidx.core.content.edit

@SuppressLint("VpnServicePolicy")
class HiddifyVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null
    private var platform: HiddifyPlatform? = null
    private val coreLock = Mutex()
    private val stopping = AtomicBoolean(false)

    private var isServiceRunning = false
    private var currentServerId = 0
    private var currentServerName = ""

    private val sdk get() = EasyHiddify.instance

    private val stateRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HiddifyPrefs.ACTION_REQUEST_VPN_STATE) {
                notifyStateChange(
                    isConnected = isServiceRunning,
                    serverId = currentServerId,
                    serverName = currentServerName,
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            /* context = */ this,
            /* receiver = */ stateRequestReceiver,
            /* filter = */ IntentFilter(HiddifyPrefs.ACTION_REQUEST_VPN_STATE),
            /* flags = */ ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == HiddifyPrefs.ACTION_STOP_VPN) {
            if (stopping.get()) return START_NOT_STICKY
            sdk.logger.append(2, "[SERVICE] Received STOP command action")

            val notification = sdk.notifications.createNotification(serverName = "")
            startForeground(HiddifyPrefs.NOTIFICATION_ID, notification)

            stopVpnInternal()
            return START_NOT_STICKY
        }

        val configContent = intent?.getStringExtra(HiddifyPrefs.CONFIG_CONTENT) ?: ""
        val serverName = intent?.getStringExtra(HiddifyPrefs.NAME_SERVER) ?: HiddifyPrefs.VPN
        val serverId = intent?.getIntExtra(HiddifyPrefs.EXTRA_SERVER_ID, 0) ?: 0
        val icon = intent?.getIntExtra(HiddifyPrefs.ICON_PUSH, 0) ?: 0
        val appsList = intent?.getStringArrayExtra(HiddifyPrefs.APPS_LIST)
        val isEnabledApps = intent?.getBooleanExtra(HiddifyPrefs.IS_ENABLED_APPS, false) ?: false
        val disconnectText = intent?.getStringExtra(HiddifyPrefs.DISCONNECT_TEXT) ?: ""

        sdk.logger.append(2, "[SERVICE] Service started with config length: ${configContent.length}")
        if (configContent.isNotEmpty()) {
            startVpn(
                configContent = configContent,
                serverName = serverName,
                serverId = serverId,
                icon = icon,
                appsList = appsList?.toList() ?: emptyList(),
                isEnabledApps = isEnabledApps,
                disconnectText = disconnectText,
            )
        } else {
            sdk.logger.append(4, "[SERVICE ERROR] Received empty configuration!")
            stopVpnInternal()
        }
        return START_STICKY
    }

    private fun startVpn(
        configContent: String,
        serverName: String,
        serverId: Int,
        @DrawableRes icon: Int,
        appsList: List<String>,
        isEnabledApps: Boolean,
        disconnectText: String,
    ) {
        if (stopping.get()) return
        val notification = sdk.notifications.createNotification(
            serverName = serverName,
            icon = icon,
            disconnectText = disconnectText,
        )
        if (!isServiceRunning) startForeground(HiddifyPrefs.NOTIFICATION_ID, notification)

        serviceScope.launch(context = Dispatchers.Main.immediate) {
            coreLock.withLock {
                if (stopping.get()) return@withLock
                try {
                    withContext(context = Dispatchers.IO) {
                        val hiddifyConfig = sdk.generator.generateConfig(configStr = configContent)
                        val core = commandServer ?: createCommandServer()
                        checkNotNull(platform).updateApps(
                            isEnabled = isEnabledApps,
                            apps = appsList,
                        )
                        core.startOrReloadService(
                            hiddifyConfig,
                            OverrideOptions(),
                        )
                        if (commandClient == null) setupCommandClient()
                        if (!stopping.get()) {
                            saveVpnState(
                                isRunning = true,
                                serverId = serverId,
                                serverName = serverName,
                            )
                        }
                    }
                    if (stopping.get()) return@withLock
                    currentServerId = serverId
                    currentServerName = serverName
                    isServiceRunning = true
                    startForeground(HiddifyPrefs.NOTIFICATION_ID, notification)
                    notifyStateChange(
                        isConnected = true,
                        serverId = serverId,
                        serverName = serverName,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    sdk.logger.append(
                        level = 4,
                        message = "[SERVICE ERROR] Failed to load VPN config: ${error.message}",
                    )
                    stopVpnInternal()
                }
            }
        }
    }

    private fun createCommandServer(): CommandServer {
        Libbox.setup(SetupOptions().apply {
            basePath = sdk.coreUtils.getWorkingDir()
            workingPath = sdk.coreUtils.getWorkingDir()
            tempPath = sdk.coreUtils.getTempDir()
            commandServerListenPort = HiddifyPrefs.COMMAND_SERVER_LISTEN_PORT
            commandServerSecret = sdk.coreUtils.generateSecret()
            fixAndroidStack = true
        })
        val vpnPlatform = HiddifyPlatform(service = this)
        platform = vpnPlatform
        return Libbox.newCommandServer(
            HiddifyCommandHandler(context = this),
            vpnPlatform,
        ).also {
            commandServer = it
            it.start()
        }
    }

    private fun setupCommandClient() {
        try {
            commandClient?.disconnect()
            commandClient = null

            sdk.logger.append(2, "[SERVICE] Connecting CommandClient...")
            val options = CommandClientOptions().apply {
                statusInterval = HiddifyPrefs.STATUS_INTERVAL
                addCommand(Libbox.CommandStatus)
            }
            commandClient = Libbox.newCommandClient(
                /* handler = */ HiddifyClientHandler(this),
                /* options = */ options,
            )
            commandClient?.connect()
            sdk.logger.append(2, "[SERVICE] CommandClient connected")
        } catch (e: Exception) {
            sdk.logger.append(4, "[SERVICE ERROR] CommandClient failed: ${e.message}")
        }
    }

    private fun saveVpnState(
        isRunning: Boolean,
        serverId: Int,
        serverName: String,
    ) {
        val prefs = getSharedPreferences(HiddifyPrefs.PREFS_NAME, MODE_PRIVATE)
        prefs.edit(commit = true) {
            putBoolean(HiddifyPrefs.KEY_IS_RUNNING, isRunning)
                .putInt(HiddifyPrefs.KEY_SERVER_ID, serverId)
                .putString(HiddifyPrefs.KEY_SERVER_NAME, serverName)
        }
    }

    private fun notifyStateChange(
        isConnected: Boolean,
        serverId: Int = currentServerId,
        serverName: String = currentServerName,
    ) {
        val intent = Intent(HiddifyPrefs.ACTION_VPN_STATE).apply {
            putExtra(HiddifyPrefs.EXTRA_IS_CONNECTED, isConnected)
            putExtra(HiddifyPrefs.EXTRA_SERVER_ID, serverId)
            putExtra(HiddifyPrefs.EXTRA_SERVER_NAME, serverName)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun stopVpnInternal() {
        if (!stopping.compareAndSet(false, true)) return
        sdk.logger.append(2, "[SERVICE] Stopping VPN service...")
        isServiceRunning = false
        notifyStateChange(
            isConnected = false,
            serverId = 0,
            serverName = "",
        )
        stopForeground(STOP_FOREGROUND_REMOVE)

        serviceScope.launch {
            coreLock.withLock {
                try {
                    saveVpnState(
                        isRunning = false,
                        serverId = 0,
                        serverName = "",
                    )
                    commandClient?.disconnect()
                    commandServer?.closeService()
                    commandServer?.close()
                    sdk.logger.append(2, "[SERVICE] Core services closed cleanly")
                } catch (e: Exception) {
                    sdk.logger.append(4, "[SERVICE ERROR] Exception while stopping VPN: ${e.message}")
                } finally {
                    stopSelf()
                    sdk.logger.append(2, "[SERVICE] VPN stopped. Exiting process...")
                    Thread.sleep(HiddifyPrefs.DELAY_BEFORE_EXIT)
                    exitProcess(0)
                }
            }
        }
    }

    override fun onRevoke() {
        sdk.logger.append(2, "[SERVICE] VPN revoked by system")
        stopVpnInternal()
        super.onRevoke()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stateRequestReceiver)
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }
}
