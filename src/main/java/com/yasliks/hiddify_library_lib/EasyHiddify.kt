package com.yasliks.hiddify_library_lib

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.hiddify.core.libbox.Libbox
import com.yasliks.easy_hiddify_lib.R
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import com.yasliks.hiddify_library_lib.service.HiddifyVpnService
import com.yasliks.hiddify_library_lib.utils.HiddifyConfigUtils
import com.yasliks.hiddify_library_lib.utils.HiddifyLoggerUtils
import com.yasliks.hiddify_library_lib.utils.HiddifyNotificationUtils
import com.yasliks.hiddify_library_lib.utils.HiddifyStateUtils
import com.yasliks.hiddify_library_lib.utils.HiddifyCoreUtils
import com.yasliks.hiddify_library_lib.utils.HiddifySettingsUtils
import go.Seq

class EasyHiddify private constructor(
    context: Context,
) {

    private val appContext = context.applicationContext

    val state = HiddifyStateUtils(appContext)
    val logger = HiddifyLoggerUtils(appContext)
    val generator = HiddifyConfigUtils(appContext)
    val coreUtils = HiddifyCoreUtils(appContext)
    val notifications = HiddifyNotificationUtils(appContext)
    val settings = HiddifySettingsUtils(appContext)

    companion object {
        @Volatile
        private var INSTANCE: EasyHiddify? = null

        /**
         * Initializes `EasyHiddify`
         *
         * @param context for library needs
         */
        fun init(context: Context): EasyHiddify {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val ctx = context.applicationContext

                    // Initializing the Go layer
                    Seq.setContext(ctx)
                    Libbox.touch()

                    val instance = EasyHiddify(ctx)
                    INSTANCE = instance
                    instance.logger.append(2, "[SDK] EasyHiddify initialized")
                    instance.state.checkVpnState()
                    instance
                }
            }
        }

        /**
         * Global access `EasyHiddify`
         */
        val instance: EasyHiddify
            get() = INSTANCE ?: throw IllegalStateException(
                "EasyHiddify must be initialized in Application.onCreate using EasyHiddify.init(this)"
            )
    }

    /**
     * Launching a VPN via Intent
     *
     * @param configStr connection configuration string
     * @param serverName the name of the notification server
     * @param icon notification icon
     * @param appsList list of tunneling applications
     * @param isEnabledApps enable the list of tunneling applications
     */
    fun startVpn(
        configStr: String,
        serverName: String = HiddifyPrefs.SERVER,
        @DrawableRes icon: Int = 0,
        serverId: Int = 0,
        appsList: List<String> = emptyList(),
        isEnabledApps: Boolean = false,
        disconnectText: String = appContext.getString(R.string.disconnect_push_button_text),
    ) {
        logger.append(2, "[SDK] Requesting VPN start. Apps count: ${appsList.size}, split tunneling enabled: $isEnabledApps")

        val intent = Intent(
            /* packageContext = */ appContext,
            /* cls = */ HiddifyVpnService::class.java,
        ).apply {
            putExtra(HiddifyPrefs.CONFIG_CONTENT, configStr)
            putExtra(HiddifyPrefs.NAME_SERVER, serverName)
            putExtra(HiddifyPrefs.ICON_PUSH, icon)
            putExtra(HiddifyPrefs.EXTRA_SERVER_ID, serverId)
            putExtra(HiddifyPrefs.APPS_LIST, appsList.toTypedArray())
            putExtra(HiddifyPrefs.IS_ENABLED_APPS, isEnabledApps)
            putExtra(HiddifyPrefs.DISCONNECT_TEXT, disconnectText)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    /**
     * Stopping VPN via Intent
     */
    fun stopVpn() {
        logger.append(2, "[SDK] Requesting VPN stop")
        val intent = Intent(
            /* packageContext = */ appContext,
            /* cls = */ HiddifyVpnService::class.java,
        ).apply {
            action = HiddifyPrefs.ACTION_STOP_VPN
        }
        ContextCompat.startForegroundService(appContext, intent)
    }
}