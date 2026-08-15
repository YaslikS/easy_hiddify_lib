package com.yasliks.hiddify_library_lib.extension

/**
 * Auxiliary function for formatting bytes in a readable form
 */
fun Long.formatTraffic(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    return if (mb > 1) "%.2f MB".format(mb) else "%.2f KB".format(kb)
}
