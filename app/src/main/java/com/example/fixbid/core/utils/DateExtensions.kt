package com.example.fixbid.core.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Extension dùng trong các DTO
fun String.toEpochMillis(): Long = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrDefault(0L)

fun Long.toFormattedDate(pattern: String = "dd/MM/yyyy HH:mm"): String {
    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern(pattern).format(dt)
}

fun Long.toRelativeTime(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 60_000       -> "Vừa xong"
        diff < 3_600_000    -> "${diff / 60_000} phút trước"
        diff < 86_400_000   -> "${diff / 3_600_000} giờ trước"
        else                -> "${diff / 86_400_000} ngày trước"
    }
}