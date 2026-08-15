package com.yasliks.hiddify_library_lib.utils

import android.content.Context
import com.hiddify.core.libbox.Libbox
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import java.io.File

class HiddifyCoreUtils(private val context: Context) {

    fun getWorkingDir(): String {
        val dir = File(context.filesDir, HiddifyPrefs.HIDDIFY_SMALL)
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    fun getTempDir(): String {
        val dir = File(context.cacheDir, HiddifyPrefs.EASY_HIDDIFY_TEMP)
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    fun generateSecret(): String {
        return Libbox.randomHex(HiddifyPrefs.LENGH_RANDOM_HEX).value
    }
}