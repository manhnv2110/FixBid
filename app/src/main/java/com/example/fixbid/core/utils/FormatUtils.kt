package com.example.fixbid.core.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val viLocale = Locale("vi", "VN")

fun formatCurrencyVnd(amount: Double): String {
    val formatter = NumberFormat.getInstance(viLocale)
    return "${formatter.format(amount.toLong())}đ"
}

fun formatDateTimeVi(timestamp: Long, pattern: String = "EEE, dd/MM/yyyy • HH:mm"): String {
    if (timestamp <= 0L) return "—"
    val sdf = SimpleDateFormat(pattern, viLocale)
    return sdf.format(Date(timestamp))
}

fun formatShortDateTime(timestamp: Long): String =
    formatDateTimeVi(timestamp, "dd/MM HH:mm")

/** "5 phút trước", "2 giờ trước", "3 ngày trước", ... */
fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return "—"
    val diff = (now - timestamp).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "Vừa xong"
        minutes < 60 -> "$minutes phút trước"
        hours < 24 -> "$hours giờ trước"
        days < 30 -> "$days ngày trước"
        else -> formatDateTimeVi(timestamp, "dd/MM/yyyy")
    }
}
