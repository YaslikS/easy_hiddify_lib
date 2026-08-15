package com.yasliks.hiddify_library_lib.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.hiddify.core.libbox.Notification
import com.hiddify.core.libbox.OutboundGroup
import com.hiddify.core.libbox.StatusMessage
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HiddifyStateUtils(context: Context) {

    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()

    private val _status = MutableStateFlow<StatusMessage?>(null)
    val status = _status.asStateFlow()

    private val _notification = MutableStateFlow<Notification?>(null)
    val notification = _notification.asStateFlow()

    private val _groups = MutableStateFlow<List<OutboundGroup>>(emptyList())
    val groups = _groups.asStateFlow()


    init {
        val filter = IntentFilter(HiddifyPrefs.ACTION_VPN_STATE)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, intent: Intent?) {
                if (intent?.action == HiddifyPrefs.ACTION_VPN_STATE) {
                    val isConnected = intent.getBooleanExtra(
                        HiddifyPrefs.EXTRA_IS_CONNECTED,
                        false,
                    )
                    _connected.value = isConnected
                    if (!isConnected) resetLocal()
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
        _groups.value = emptyList()
    }
}