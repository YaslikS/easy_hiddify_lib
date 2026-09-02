package com.yasliks.hiddify_library_lib.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.hiddify.core.libbox.Notification
import com.hiddify.core.libbox.OutboundGroup
import com.hiddify.core.libbox.StatusMessage
import com.yasliks.hiddify_library_lib.model.TrafficStats
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HiddifyStateUtils(context: Context) {

    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()

    private val _status = MutableStateFlow<StatusMessage?>(null)
    val status = _status.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStats())
    val trafficStats = _trafficStats.asStateFlow()

    private val _notification = MutableStateFlow<Notification?>(null)
    val notification = _notification.asStateFlow()

    private val _groups = MutableStateFlow<List<OutboundGroup>>(emptyList())
    val groups = _groups.asStateFlow()

    init {
        val filter = IntentFilter().apply {
            addAction(HiddifyPrefs.ACTION_VPN_STATE)
            addAction(HiddifyPrefs.ACTION_VPN_TRAFFIC)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, intent: Intent?) {
                when (intent?.action) {
                    HiddifyPrefs.ACTION_VPN_STATE -> {
                        val isConnected = intent.getBooleanExtra(
                            /* name = */ HiddifyPrefs.EXTRA_IS_CONNECTED,
                            /* defaultValue = */ false,
                        )
                        _connected.value = isConnected
                        if (!isConnected) resetLocal()
                    }
                    HiddifyPrefs.ACTION_VPN_TRAFFIC -> {
                        val uplink = intent.getLongExtra(HiddifyPrefs.EXTRA_UPLINK_SPEED, 0L)
                        val downlink = intent.getLongExtra(HiddifyPrefs.EXTRA_DOWNLINK_SPEED, 0L)
                        val uplinkTotal = intent.getLongExtra(HiddifyPrefs.EXTRA_UPLINK_TOTAL, 0L)
                        val downlinkTotal = intent.getLongExtra(HiddifyPrefs.EXTRA_DOWNLINK_TOTAL, 0L)

                        _trafficStats.value = TrafficStats(
                            uplinkSpeed = uplink,
                            downlinkSpeed = downlink,
                            uplinkTotal = uplinkTotal,
                            downlinkTotal = downlinkTotal,
                        )
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            /* context = */ context,
            /* receiver = */ receiver,
            /* filter = */ filter,
            /* flags = */ ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    internal fun updateNotification(value: Notification?) {
        _notification.value = value
    }

    internal fun updateConnected(value: Boolean) {
        _connected.value = value
    }

    internal fun updateStatus(message: StatusMessage?) {
        _status.value = message
    }

    internal fun updateGroups(list: List<OutboundGroup>) {
        _groups.value = list
    }

    private fun resetLocal() {
        _status.value = null
        _trafficStats.value = TrafficStats()
        _groups.value = emptyList()
    }
}