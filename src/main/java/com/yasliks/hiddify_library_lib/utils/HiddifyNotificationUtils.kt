package com.yasliks.hiddify_library_lib.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import com.hiddify.core.libbox.Libbox
import com.hiddify.core.libbox.Notification
import com.yasliks.easy_hiddify_lib.R
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import com.yasliks.hiddify_library_lib.service.HiddifyVpnService

class HiddifyNotificationUtils(
    private val context: Context,
) {
    @DrawableRes
    var currentIcon = 0
    private var currentServerName = ""
    private var currentDisconnectText = ""

    @SuppressLint("ObsoleteSdkInt")
    fun createNotification(
        serverName: String? = null,
        @DrawableRes icon: Int = 0,
        notification: Notification? = null,
        downlinkSpeed: Long = 0L,
        uplinkSpeed: Long = 0L,
        downlinkTotal: Long = 0L,
        uplinkTotal: Long = 0L,
        disconnectText: String? = null,
    ): android.app.Notification {
        if (icon != 0) {
            currentIcon = icon
        }
        if (!serverName.isNullOrEmpty()) {
            currentServerName = serverName
        }
        if (!disconnectText.isNullOrEmpty()) {
            currentDisconnectText = disconnectText
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                /* id = */ HiddifyPrefs.CHANNEL_ID,
                /* name = */ HiddifyPrefs.VPN_STATUS,
                /* importance = */ NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        val title = notification?.title ?: currentServerName.ifEmpty { HiddifyPrefs.VPN }

        val downSpeedStr = "${Libbox.formatBytes(downlinkSpeed)}/s"
        val upSpeedStr = "${Libbox.formatBytes(uplinkSpeed)}/s"
        val totalDownStr = Libbox.formatBytes(downlinkTotal)
        val totalUpStr = Libbox.formatBytes(uplinkTotal)

        val speedLine = context.getString(R.string.speed, "⬇️ $downSpeedStr || ⬆️ $upSpeedStr")
        val totalLine = context.getString(R.string.total,"⬇️ $totalDownStr || ⬆️ $totalUpStr")
        val fullText = "$speedLine\n$totalLine"

        val iconToSet = if (icon != 0) icon else if (currentIcon != 0) currentIcon else R.drawable.outline_vpn_lock_24

        val stopIntent = Intent(context, HiddifyVpnService::class.java).apply {
            action = HiddifyPrefs.ACTION_STOP_VPN
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val extras = Bundle()
        if (Build.VERSION.SDK_INT >= 36) {
            extras.putBoolean("android.requestPromotedOngoing", true)

            val chipText = if (title.length > 8) {
                "${title.take(8).trim()}…"
            } else {
                title
            }

            extras.putCharSequence("android.shortCriticalText", chipText)
        }

        return NotificationCompat.Builder(context, HiddifyPrefs.CHANNEL_ID)
            .setSmallIcon(iconToSet)
            .setContentTitle(title)
            .setContentText(speedLine)
            .setColor(android.graphics.Color.parseColor("#4CAF50"))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setTicker(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addExtras(extras)
            .addAction(0, currentDisconnectText, stopPendingIntent)
            .build()
    }

    fun updateNotificationTraffic(
        downlinkSpeed: Long,
        uplinkSpeed: Long,
        downlinkTotal: Long,
        uplinkTotal: Long,
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(
            downlinkSpeed = downlinkSpeed,
            uplinkSpeed = uplinkSpeed,
            downlinkTotal = downlinkTotal,
            uplinkTotal = uplinkTotal,
        )
        manager.notify(HiddifyPrefs.NOTIFICATION_ID, notification)
    }
}