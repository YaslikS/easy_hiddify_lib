package com.yasliks.hiddify_library_lib.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import com.hiddify.core.libbox.Notification
import com.yasliks.easy_hiddify_lib.R
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs

class HiddifyNotificationUtils(
    private val context: Context,
) {
    @DrawableRes
    var currentIcon: Int = 0

    @SuppressLint("ObsoleteSdkInt")
    fun createNotification(
        serverName: String?,
        @DrawableRes icon: Int = 0,
        notification: Notification? = null
    ): android.app.Notification {
        if (icon != 0) {
            currentIcon = icon
        }

        val manager = context.getSystemService(
            /* name = */ Context.NOTIFICATION_SERVICE,
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                /* id = */ HiddifyPrefs.CHANNEL_ID,
                /* name = */ HiddifyPrefs.VPN_STATUS,
                /* importance = */ NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val title = notification?.title ?: serverName ?: HiddifyPrefs.VPN
        val content = notification?.body ?: HiddifyPrefs.CONNECTED

        val iconToSet = if (icon != 0) {
            icon
        } else {
            if (currentIcon != 0) {
                currentIcon
            } else {
                R.drawable.outline_vpn_lock_24
            }
        }

        return NotificationCompat.Builder(context, HiddifyPrefs.CHANNEL_ID)
            .setSmallIcon(iconToSet)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

}