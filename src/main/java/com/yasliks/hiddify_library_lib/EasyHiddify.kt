package com.yasliks.hiddify_library_lib

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import com.hiddify.core.libbox.Libbox
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import com.yasliks.hiddify_library_lib.service.HiddifyVpnService
import com.yasliks.hiddify_library_lib.utils.HiddifyConfigUtils
import com.yasliks.hiddify_library_lib.utils.HiddifyLoggerUtils
import com.yasliks.hiddify_library_lib.utils.HiddifyNotificationUtils
import com.yasliks.hiddify_library_lib.utils.HiddifyStateUtils
import com.yasliks.hiddify_library_lib.utils.HiddifyCoreUtils
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
     * @param appsList список приложения для туннелирования
     * @param isEnabledApps включить список приложения для туннелирования
     */
    fun startVpn(
        configStr: String,
        serverName: String = HiddifyPrefs.SERVER,
        @DrawableRes icon: Int = 0,
        appsList: List<String> = emptyList(),
        isEnabledApps: Boolean = false,
    ) {
        logger.append(2, "[SDK] Requesting VPN start. Apps count: ${appsList.size}, split tunneling enabled: $isEnabledApps")

        val intent = Intent(
            /* packageContext = */ appContext,
            /* cls = */ HiddifyVpnService::class.java,
        ).apply {
            putExtra(HiddifyPrefs.CONFIG_CONTENT, configStr)
            putExtra(HiddifyPrefs.NAME_SERVER, serverName)
            putExtra(HiddifyPrefs.ICON_PUSH, icon)
            putExtra(HiddifyPrefs.APPS_LIST, appsList.toTypedArray())
            putExtra(HiddifyPrefs.IS_ENABLED_APPS, isEnabledApps)
        }
        appContext.startService(intent)
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
        appContext.startService(intent)
    }
}