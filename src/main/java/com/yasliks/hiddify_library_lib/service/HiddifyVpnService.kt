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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
import androidx.core.content.edit

@SuppressLint("VpnServicePolicy")
class HiddifyVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null

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
            sdk.logger.append(2, "[SERVICE] Received STOP command action")
            stopVpnInternal()
            return START_NOT_STICKY
        }

        val configContent = intent?.getStringExtra(HiddifyPrefs.CONFIG_CONTENT) ?: ""
        val serverName = intent?.getStringExtra(HiddifyPrefs.NAME_SERVER) ?: HiddifyPrefs.VPN
        val serverId = intent?.getIntExtra(HiddifyPrefs.EXTRA_SERVER_ID, 0) ?: 0
        val icon = intent?.getIntExtra(HiddifyPrefs.ICON_PUSH, 0) ?: 0
        val appsList = intent?.getStringArrayExtra(HiddifyPrefs.APPS_LIST)
        val isEnabledApps = intent?.getBooleanExtra(HiddifyPrefs.IS_ENABLED_APPS, false) ?: false

        sdk.logger.append(2, "[SERVICE] Service started with config length: ${configContent.length}")
        if (configContent.isNotEmpty()) {
            startVpn(
                configContent = configContent,
                serverName = serverName,
                serverId = serverId,
                icon = icon,
                appsList = appsList?.toList() ?: emptyList(),
                isEnabledApps = isEnabledApps,
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
    ) {
        currentServerId = serverId
        currentServerName = serverName

        val notification = sdk.notifications.createNotification(
            serverName = serverName,
            icon = icon,
        )
        startForeground(HiddifyPrefs.NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                sdk.logger.append(2, "[SERVICE] Starting VPN process...")
                val hiddifyConfig = sdk.generator.generateConfig(configContent)

                Libbox.setup(SetupOptions().apply {
                    basePath = sdk.coreUtils.getWorkingDir()
                    workingPath = sdk.coreUtils.getWorkingDir()
                    tempPath = sdk.coreUtils.getTempDir()
                    commandServerListenPort = HiddifyPrefs.COMMAND_SERVER_LISTEN_PORT
                    commandServerSecret = sdk.coreUtils.generateSecret()
                    fixAndroidStack = true
                })
                sdk.logger.append(2, "[SERVICE] Libbox setup completed")

                commandServer = Libbox.newCommandServer(
                    /* handler = */ HiddifyCommandHandler(this@HiddifyVpnService),
                    /* platformInterface = */ HiddifyPlatform(
                        service = this@HiddifyVpnService,
                        appsList = appsList,
                        isEnabledApps = isEnabledApps,
                    ),
                )
                commandServer?.start()
                sdk.logger.append(2, "[SERVICE] CommandServer started")

                commandServer?.startOrReloadService(
                    /* configContent = */ hiddifyConfig,
                    /* options = */ OverrideOptions(),
                )
                sdk.logger.append(2, "[SERVICE] Core service loaded successfully")

                isServiceRunning = true
                saveVpnState(isRunning = true, serverId = serverId, serverName = serverName)
                notifyStateChange(true, serverId, serverName)

                setupCommandClient()
                sdk.logger.append(2, "[SERVICE] VPN started successfully!")
            } catch (e: Exception) {
                sdk.logger.append(4, "[SERVICE ERROR] Failed to start VPN: ${e.message}")
                stopVpnInternal()
            }
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

    private fun saveVpnState(isRunning: Boolean, serverId: Int, serverName: String) {
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
        sdk.logger.append(2, "[SERVICE] Stopping VPN service...")
        isServiceRunning = false
        saveVpnState(isRunning = false, serverId = 0, serverName = "")
        notifyStateChange(false, 0, "")
        stopForeground(STOP_FOREGROUND_REMOVE)

        Thread {
            try {
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
        }.start()
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