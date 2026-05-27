package com.example.fixbid.core.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Parse an ISO-8601 / Postgrest timestamp into epoch millis.
 *
 * Handles every common shape Supabase emits:
 * - `2025-01-15T10:30:00Z`                 (UTC, ISO_INSTANT)
 * - `2025-01-15T10:30:00.123456Z`          (with fractional seconds)
 * - `2025-01-15T10:30:00+07:00`            (offset with full minutes)
 * - `2025-01-15T10:30:00+00`               (Postgrest short offset – no minutes)
 * - `2025-01-15 10:30:00+00`               (Postgrest with space separator)
 * - `2025-01-15T10:30:00`                  (no offset – assumed UTC)
 *
 * Returns `0L` on any failure rather than throwing, since the call sites
 * already render `0L` as a friendly em-dash.
 */
fun String.toEpochMillis(): Long {
    if (isBlank()) return 0L

    // Normalise Postgrest quirks before handing the string to java.time:
    //   - swap the space separator with the canonical `T`
    //   - expand short numeric offsets ("+00" / "-05") to "+00:00" / "-05:00"
    val normalized = this
        .trim()
        .replace(' ', 'T')
        .let { s ->
            // Match a trailing "+HH" or "-HH" with no minutes and pad it.
            Regex("([+-])(\\d{2})$").replace(s) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}:00"
            }
        }

    return runCatching {
        // 1. UTC `Z` form — fastest path.
        Instant.parse(normalized).toEpochMilli()
    }.recoverCatching {
        // 2. Any explicit offset (`+07:00`, `-05:30`, …).
        OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
    }.recoverCatching {
        // 3. Bare local datetime (no zone) — assume UTC.
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
