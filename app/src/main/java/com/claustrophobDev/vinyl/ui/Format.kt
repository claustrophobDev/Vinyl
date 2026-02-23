package com.claustrophobDev.vinyl.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ru: Locale = Locale.forLanguageTag("ru")

fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_048_576 -> String.format(ru, "%.1f МБ/с", bytesPerSecond / 1_048_576f)
    bytesPerSecond >= 1024 -> "${bytesPerSecond / 1024} КБ/с"
    else -> "$bytesPerSecond Б/с"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(ru, "%.2f ГБ", bytes / 1_073_741_824f)
    bytes >= 1_048_576 -> String.format(ru, "%.1f МБ", bytes / 1_048_576f)
    bytes >= 1024 -> "${bytes / 1024} КБ"
    else -> "$bytes Б"
}

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0) return "не обновлялась"
    val minutes = (now - timestamp) / 60_000
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 24 * 60 -> "${minutes / 60} ч назад"
        else -> "${minutes / (24 * 60)} дн назад"
    }
}

fun formatDate(timestamp: Long): String = SimpleDateFormat("d MMM yyyy", ru).format(Date(timestamp))

fun serversWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "серверов"
        mod10 == 1 -> "сервер"
        mod10 in 2..4 -> "сервера"
        else -> "серверов"
    }
}
