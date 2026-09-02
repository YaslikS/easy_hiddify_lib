package com.yasliks.hiddify_library_lib.model

import com.hiddify.core.libbox.Libbox
import java.util.Locale
import kotlin.math.round

data class TrafficStats(
    val uplinkSpeed: Long = 0L,     // byte/sec
    val downlinkSpeed: Long = 0L,   // byte/sec
    val uplinkTotal: Long = 0L,     // byte
    val downlinkTotal: Long = 0L,   // byte
) {
    fun getUplinkSpeed(): String = "${Libbox.formatBytes(uplinkSpeed)}/s"
    fun getDownlinkSpeed(): String = "${Libbox.formatBytes(downlinkSpeed)}/s"
    fun getUplinkTotal(): String = Libbox.formatBytes(uplinkTotal)
    fun getDownlinkTotal(): String = Libbox.formatBytes(downlinkTotal)

    fun getDownlinkTotalMegaBytes(): String {
        val megaBytes = getTotalMegaBytesValue()
        return String.format(Locale.US, "%.2f MB", megaBytes)
    }

    fun getTotalMegaBytesValue(): Double {
        val mb = downlinkTotal / (1024.0 * 1024.0)
        return round(mb * 100.0) / 100.0
    }
}