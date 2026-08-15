package com.yasliks.hiddify_library_lib.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.hiddify.core.libbox.LogEntry
import com.hiddify.core.libbox.LogIterator
import com.yasliks.hiddify_library_lib.model.LogItem
import com.yasliks.hiddify_library_lib.prefs.HiddifyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

class HiddifyLoggerUtils(private val context: Context) {
    private val idCounter = AtomicLong(0)
    private val _logs = MutableStateFlow<List<LogItem>>(emptyList())
    val logs = _logs.asStateFlow()

    init {
        val filter = IntentFilter().apply {
            addAction(HiddifyPrefs.ACTION_LOG_EVENT)
            addAction(HiddifyPrefs.ACTION_LOG_CLEAR)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, intent: Intent?) {
                when (intent?.action) {
                    HiddifyPrefs.ACTION_LOG_EVENT -> {
                        val level = intent.getIntExtra(HiddifyPrefs.EXTRA_LOG_LEVEL, 2)
                        val message = intent.getStringExtra(HiddifyPrefs.EXTRA_LOG_MESSAGE) ?: return
                        addLogToState(level, message)
                    }
                    HiddifyPrefs.ACTION_LOG_CLEAR -> {
                        _logs.value = emptyList()
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

    private fun addLogToState(level: Int, message: String) {
        val item = LogItem(
            id = idCounter.incrementAndGet(),
            level = level,
            message = message,
        )

        _logs.update { currentList ->
            val updated = currentList.toMutableList()
            updated.add(item)
            if (updated.size > HiddifyPrefs.MAX_LOGS) {
                updated.removeAt(0)
            }
            updated
        }
    }

    fun append(level: Int, message: String) {
        when (level) {
            0 -> Log.v(HiddifyPrefs.HIDDIFY_LIB, message)
            1 -> Log.d(HiddifyPrefs.HIDDIFY_LIB, message)
            2 -> Log.i(HiddifyPrefs.HIDDIFY_LIB, message)
            3 -> Log.w(HiddifyPrefs.HIDDIFY_LIB, message)
            else -> Log.e(HiddifyPrefs.HIDDIFY_LIB, message)
        }

        try {
            val intent = Intent(HiddifyPrefs.ACTION_LOG_EVENT).apply {
                putExtra(HiddifyPrefs.EXTRA_LOG_LEVEL, level)
                putExtra(HiddifyPrefs.EXTRA_LOG_MESSAGE, message)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            addLogToState(level, message)
        }
    }

    fun append(entry: LogEntry) {
        append(entry.level, entry.message)
    }

    fun append(iterator: LogIterator) {
        while (iterator.hasNext()) {
            val entry = iterator.next()
            append(entry.level, entry.message)
        }
    }

    fun clear() {
        try {
            val intent = Intent(HiddifyPrefs.ACTION_LOG_CLEAR).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            _logs.value = emptyList()
        }
    }
}