package com.claustrophobDev.vinyl.vpn

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VpnStatus { IDLE, CONNECTING, CONNECTED, STOPPING, ERROR }

data class Traffic(
    val upSpeed: Long = 0,
    val downSpeed: Long = 0,
    val upTotal: Long = 0,
    val downTotal: Long = 0
)

// сервис и ui в одном процессе, так что состояние просто в object
object VpnState {
    val status = MutableStateFlow(VpnStatus.IDLE)
    val error = MutableStateFlow<String?>(null)
    val connectedAt = MutableStateFlow(0L) // SystemClock.elapsedRealtime() в момент подключения
    val serverName = MutableStateFlow<String?>(null)
    val traffic = MutableStateFlow(Traffic())
}

enum class LogLevel { INFO, WARN, ERROR }

data class LogLine(val time: Long, val level: LogLevel, val text: String)

object VpnLog {
    private const val MAX = 300

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines

    fun info(msg: String) = add(LogLevel.INFO, msg)
    fun warn(msg: String) = add(LogLevel.WARN, msg)
    fun error(msg: String) = add(LogLevel.ERROR, msg)

    fun add(level: LogLevel, msg: String) {
        val text = msg.trim()
        if (text.isEmpty()) return
        Log.println(if (level == LogLevel.ERROR) Log.ERROR else Log.INFO, "Vinyl", text)
        _lines.update { (it + LogLine(System.currentTimeMillis(), level, text)).takeLast(MAX) }
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun asText(): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        return _lines.value.joinToString("\n") { fmt.format(Date(it.time)) + " " + it.level + " " + it.text }
    }
}
