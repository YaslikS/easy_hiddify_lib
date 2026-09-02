package com.yasliks.hiddify_library_lib.core

import android.content.Context
import android.content.Intent
import com.hiddify.core.libbox.*
import com.yasliks.hiddify_library_lib.EasyHiddify
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs

class HiddifyClientHandler(
    private val context: Context,
) : CommandClientHandler {

    private val sdk get() = EasyHiddify.instance
    private var lastBroadcastTime = 0L

    override fun connected() {
        sdk.logger.append(2, "[CORE CLIENT] Core connected successfully")
        sdk.state.updateConnected(true)
    }

    override fun disconnected(message: String?) {
        sdk.logger.append(3, "[CORE CLIENT] Core disconnected: ${message ?: "No reason provided"}")
        sdk.state.updateConnected(false)
    }

    override fun writeLogs(messageList: LogIterator) {
        sdk.logger.append(messageList)
    }

    override fun writeStatus(message: StatusMessage) {
        val now = System.currentTimeMillis()
        if (now - lastBroadcastTime < 1000) return
        lastBroadcastTime = now

        sdk.notifications.updateNotificationTraffic(
            downlinkSpeed = message.downlink,
            uplinkSpeed = message.uplink,
            downlinkTotal = message.downlinkTotal,
            uplinkTotal = message.uplinkTotal
        )

        try {
            val intent = Intent(HiddifyPrefs.ACTION_VPN_TRAFFIC).apply {
                putExtra(HiddifyPrefs.EXTRA_UPLINK_SPEED, message.uplink)
                putExtra(HiddifyPrefs.EXTRA_DOWNLINK_SPEED, message.downlink)
                putExtra(HiddifyPrefs.EXTRA_UPLINK_TOTAL, message.uplinkTotal)
                putExtra(HiddifyPrefs.EXTRA_DOWNLINK_TOTAL, message.downlinkTotal)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            sdk.logger.append(4, "[CORE CLIENT ERROR] Failed to send traffic broadcast: ${e.message}")
        }

        sdk.state.updateStatus(message)
    }

    override fun writeGroups(message: OutboundGroupIterator) {
        val groups = mutableListOf<OutboundGroup>()
        while (message.hasNext()) {
            groups.add(message.next())
        }
        sdk.state.updateGroups(groups)
    }

    override fun writeConnectionEvents(events: ConnectionEvents) {}
    override fun initializeClashMode(modeList: StringIterator, currentMode: String) {}
    override fun updateClashMode(newMode: String) {}
    override fun setDefaultLogLevel(level: Int) {}

    override fun clearLogs() {
        sdk.logger.clear()
    }
}