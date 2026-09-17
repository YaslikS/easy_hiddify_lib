package com.yasliks.hiddify_library_lib.utils

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.hiddify.core.libbox.Notification
import com.hiddify.core.libbox.OutboundGroup
import com.hiddify.core.libbox.StatusMessage
import com.yasliks.hiddify_library_lib.model.TrafficStats
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HiddifyStateUtils(private val context: Context) {

    private val _connected = MutableStateFlow(checkInitialConnected())
    val connected = _connected.asStateFlow()

    private val _activeServerId = MutableStateFlow(checkInitialServerId())
    val activeServerId = _activeServerId.asStateFlow()

    private val _activeServerName = MutableStateFlow(checkInitialServerName())
    val activeServerName = _activeServerName.asStateFlow()

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
                        val serverId = intent.getIntExtra(HiddifyPrefs.EXTRA_SERVER_ID, 0)
                        val serverName = intent.getStringExtra(HiddifyPrefs.EXTRA_SERVER_NAME) ?: ""

                        _connected.value = isConnected
                        if (isConnected) {
                            if (serverId != 0) _activeServerId.value = serverId
                            if (serverName.isNotEmpty()) _activeServerName.value = serverName
                        } else {
                            resetLocal()
                        }
                    }
                    HiddifyPrefs.ACTION_VPN_TRAFFIC -> {
                        if (!_connected.value) {
                            _connected.value = true
                        }
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

        checkVpnState()
    }

    fun checkVpnState() {
        try {
            val intent = Intent(HiddifyPrefs.ACTION_REQUEST_VPN_STATE).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun isVpnActiveOnSystem(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun checkInitialConnected(): Boolean {
        val prefs = context.getSharedPreferences(HiddifyPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean(HiddifyPrefs.KEY_IS_RUNNING, false)
        return isRunning && isVpnActiveOnSystem()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun checkInitialServerId(): Int? {
        if (!checkInitialConnected()) return null
        val prefs = context.getSharedPreferences(HiddifyPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(HiddifyPrefs.KEY_SERVER_ID, 0)
        return if (id != 0) id else null
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun checkInitialServerName(): String {
        if (!checkInitialConnected()) return ""
        val prefs = context.getSharedPreferences(HiddifyPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(HiddifyPrefs.KEY_SERVER_NAME, "") ?: ""
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
        _connected.value = false
        _activeServerId.value = null
        _activeServerName.value = ""
        _status.value = null
        _trafficStats.value = TrafficStats()
        _groups.value = emptyList()
    }
}