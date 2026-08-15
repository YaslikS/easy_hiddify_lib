package com.yasliks.hiddify_library_lib.core

import android.content.Context
import android.util.Log
import com.hiddify.core.libbox.CommandServerHandler
import com.hiddify.core.libbox.SystemProxyStatus
import com.yasliks.easy_hiddify_lib.R
import com.yasliks.hiddify_library_lib.EasyHiddify
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs

class HiddifyCommandHandler(
    private val context: Context,
) : CommandServerHandler {

    private val sdk get() = EasyHiddify.instance

    override fun getSystemProxyStatus(): SystemProxyStatus {
        return SystemProxyStatus().apply {
            available = false
            enabled = false
        }
    }

    override fun serviceReload() {
        sdk.logger.append(
            level = 2,
            message = context.getString(R.string.core_requested_service_reload),
        )
    }

    override fun serviceStop() {
        sdk.logger.append(
            level = 2,
            message = context.getString(R.string.core_requested_service_stop),
        )
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {

    }

    override fun writeDebugMessage(message: String) {
        Log.e(HiddifyPrefs.CORE, message)
        sdk.logger.append(
            level = 1,
            message = context.getString(R.string.core_debug, message),
        )
    }
}