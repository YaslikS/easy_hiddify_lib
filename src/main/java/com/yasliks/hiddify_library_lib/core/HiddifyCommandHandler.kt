package com.yasliks.hiddify_library_lib.core

import android.content.Context
import com.hiddify.core.libbox.CommandServerHandler
import com.hiddify.core.libbox.SystemProxyStatus
import com.yasliks.easy_hiddify_lib.R
import com.yasliks.hiddify_library_lib.EasyHiddify

class HiddifyCommandHandler(
    private val context: Context,
) : CommandServerHandler {

    private val sdk get() = EasyHiddify.instance

    override fun connectSSHAgent(): Int {
        throw Exception("SSH agent is not supported")
    }

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

    override fun setSystemProxyEnabled(enabled: Boolean) {}

    override fun triggerNativeCrash() {}

    override fun writeDebugMessage(message: String?) {
        val msg = message ?: ""
        sdk.logger.append(
            level = 1,
            message = context.getString(R.string.core_debug, msg),
        )
    }
}