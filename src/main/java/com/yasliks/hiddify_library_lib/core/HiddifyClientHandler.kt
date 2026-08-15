package com.yasliks.hiddify_library_lib.core

import android.content.Context
import android.util.Log
import com.hiddify.core.libbox.*
import com.yasliks.hiddify_library_lib.EasyHiddify

class HiddifyClientHandler(
    private val context: Context,
) : CommandClientHandler {

    private val tag = this.javaClass.simpleName

    private val sdk get() = EasyHiddify.instance

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
        Log.i(tag, "writeStatus -> ${message.uplink} | ${message.downlink}")
        sdk.state.updateStatus(message)
    }

    override fun writeGroups(message: OutboundGroupIterator) {
        val groups = mutableListOf<OutboundGroup>()
        while (message.hasNext()) {
            groups.add(message.next())
        }
        sdk.state.updateGroups(groups)
    }

    override fun writeConnectionEvents(events: ConnectionEvents) {

    }

    override fun initializeClashMode(modeList: StringIterator, currentMode: String) {}

    override fun updateClashMode(newMode: String) {}

    override fun setDefaultLogLevel(level: Int) {}

    override fun clearLogs() {
        sdk.logger.clear()
    }
}