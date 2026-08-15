package com.yasliks.hiddify_library_lib.service

import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.annotation.DrawableRes
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

@SuppressLint("VpnServicePolicy")
class HiddifyVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null

    private val sdk get() = EasyHiddify.instance

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

        val configContent = intent?.getStringExtra(
            /* name = */ HiddifyPrefs.CONFIG_CONTENT,
        ) ?: ""
        val serverName = intent?.getStringExtra(
            /* name = */ HiddifyPrefs.NAME_SERVER,
        ) ?: HiddifyPrefs.VPN
        val icon = intent?.getIntExtra(
            /* name = */ HiddifyPrefs.ICON_PUSH,
            /* defaultValue = */ 0,
        ) ?: 0
        val appsList = intent?.getStringArrayExtra(
            /* name = */ HiddifyPrefs.APPS_LIST,
        )
        val isEnabledApps = intent?.getBooleanExtra(
            /* name = */ HiddifyPrefs.IS_ENABLED_APPS,
            /* defaultValue = */ false,
        ) ?: false

        sdk.logger.append(2, "[SERVICE] Service started with config length: ${configContent.length}")
        if (configContent.isNotEmpty()) {
            startVpn(
                configContent = configContent,
                serverName = serverName,
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

    /**
     * Starts a VPN connection using the transmitted configuration
     *
     * @param configContent connection configuration string
     * @param serverName the name of the notification server
     * @param icon notification icon
     * @param appsList список приложения для туннелирования
     * @param isEnabledApps включить список приложения для туннелирования
     */
    private fun startVpn(
        configContent: String,
        serverName: String,
        @DrawableRes icon: Int,
        appsList: List<String>,
        isEnabledApps: Boolean,
    ) {
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

                notifyStateChange(true)
                setupCommandClient()
                sdk.logger.append(2, "[SERVICE] VPN started successfully!")
            } catch (e: Exception) {
                sdk.logger.append(4, "[SERVICE ERROR] Failed to start VPN: ${e.message}")
                stopVpnInternal()
            }
        }
    }

    /**
     * Initializes the client and starts the connection
     */
    private fun setupCommandClient() {
        try {
            sdk.logger.append(2, "[SERVICE] Connecting CommandClient...")
            val options = CommandClientOptions()
                .apply {
                    statusInterval = HiddifyPrefs.STATUS_INTERVAL
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

    /**
     * Notifies the VPN connection status
     *
     * @param isConnected transmitted connection status
     */
    private fun notifyStateChange(isConnected: Boolean) {
        val intent = Intent(HiddifyPrefs.ACTION_VPN_STATE).apply {
            putExtra(HiddifyPrefs.EXTRA_IS_CONNECTED, isConnected)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    /**
     * Closes the VPN connection
     */
    private fun stopVpnInternal() {
        sdk.logger.append(2, "[SERVICE] Stopping VPN service...")
        notifyStateChange(false)
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

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}