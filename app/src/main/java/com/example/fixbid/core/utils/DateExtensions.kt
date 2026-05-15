package com.example.fixbid.core.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Parse ISO-8601 timestamp string to epoch millis.
 * Handles both "2025-01-15T10:30:00Z" and "2025-01-15T10:30:00+07:00" formats.
 * Also handles Supabase format "2025-01-15 10:30:00+00" (space instead of T).
 */
fun String.toEpochMillis(): Long {
    if (isBlank()) return 0L
    return runCatching {
        // Try standard ISO-8601 first
        Instant.parse(this).toEpochMilli()
    }.recoverCatching {
        // Supabase sometimes returns "2025-01-15 10:30:00.123456+00"
        // Replace space with T and handle timezone
        val normalized = this
            .replace(" ", "T")
            .replace(Regex("\\+00$"), "+00:00")
            .replace(Regex("\\+00:00:00$"), "+00:00")
        Instant.parse(normalized).toEpochMilli()
    }.recoverCatching {
        // Try parsing as LocalDateTime (no timezone) and assume UTC
        val normalized = this.replace(" ", "T").substringBefore("+").substringBefore("-", this)
        LocalDateTime.parse(normalized.take(19))
            .atZone(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

fun Long.toFormattedDate(pattern: String = "dd/MM/yyyy HH:mm"): String {
    if (this <= 0L) return ""
    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern(pattern).format(dt)
}

fun Long.toRelativeTime(): String {
    if (this <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - this

    // Nếu timestamp ở tương lai hoặc quá xa (>365 ngày), hiển thị ngày cụ thể
    if (diff < 0 || diff > 365L * 86_400_000L) {
        return toFormattedDate("dd/MM/yyyy")
    }

    return when {
        diff < 60_000L       -> "Vừa xong"
        diff < 3_600_000L    -> "${diff / 60_000L} phút trước"
        diff < 86_400_000L   -> "${diff / 3_600_000L} giờ trước"
        diff < 2_592_000_000L -> "${diff / 86_400_000L} ngày trước"  // < 30 ngày
        diff < 31_536_000_000L -> "${diff / 2_592_000_000L} tháng trước"
        else -> toFormattedDate("dd/MM/yyyy")
    }
}
