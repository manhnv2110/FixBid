package com.example.fixbid.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Material 3 date + time selector pair.
 *
 * Renders two large tap targets that mirror the look of the surrounding form fields and
 * opens the platform DatePicker / TimePicker dialogs. When [scheduledAtMillis] is null
 * the field shows a helper hint instead of a value, so the user clearly sees the slot
 * is empty.
 *
 * @param scheduledAtMillis current selection or `null` for "not set yet".
 * @param onScheduledAtChange called with the new combined epoch-millis whenever the user
 *        picks a date or a time. The component preserves the previously chosen time
 *        when the user opens the date picker again, and vice versa.
 * @param minDateMillis if non-null, only allow days >= this date (defaults to today).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDateTimePicker(
    scheduledAtMillis: Long?,
    onScheduledAtChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    minDateMillis: Long = todayStartMillis()
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateLabel = scheduledAtMillis?.let { formatDate(it) } ?: "Chọn ngày"
    val timeLabel = scheduledAtMillis?.let { formatTime(it) } ?: "Chọn giờ"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PickerField(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.CalendarToday,
            label = "Ngày",
            value = dateLabel,
            isSet = scheduledAtMillis != null,
            onClick = { showDatePicker = true }
        )
        PickerField(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Schedule,
            label = "Giờ",
            value = timeLabel,
            isSet = scheduledAtMillis != null,
            onClick = { showTimePicker = true }
        )
    }

    if (showDatePicker) {
        val initialMillis = scheduledAtMillis ?: minDateMillis
        // DatePicker reasons in UTC midnight values; convert from the local-zoned
        // selection to UTC-anchored millis when seeding state.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = toUtcDateMillis(initialMillis),
            selectableDates = object : SelectableDates {
                private val minUtc = toUtcDateMillis(minDateMillis)
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= minUtc
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedUtc = datePickerState.selectedDateMillis
                        if (pickedUtc != null) {
                            // Combine the new Y/M/D with the previously selected H/M
                            // (or 09:00 default if this is the first pick).
                            val combined = combineDateUtcWithLocalTime(
                                dateUtcMillis = pickedUtc,
                                existingLocalMillis = scheduledAtMillis,
                                fallbackHour = 9,
                                fallbackMinute = 0
                            )
                            onScheduledAtChange(combined)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Xác nhận", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Huỷ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        "Chọn ngày",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                    todayContentColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = scheduledAtMillis ?: defaultStartOfNextHour()
        }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val base = scheduledAtMillis ?: minDateMillis
                        val combined = combineDateLocalWithTime(
                            dateMillis = base,
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        onScheduledAtChange(combined)
                        showTimePicker = false
                    }
                ) {
                    Text("Xác nhận", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Huỷ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            title = {
                Text(
                    "Chọn giờ",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = MaterialTheme.colorScheme.primary,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun PickerField(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    isSet: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSet) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .height(56.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSet) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSet) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── helpers ─────────────────────────────────────────────────────────────────

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("EEE, dd/MM/yyyy", Locale("vi", "VN")).format(Date(timestamp))

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale("vi", "VN")).format(Date(timestamp))

private fun todayStartMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun defaultStartOfNextHour(): Long = Calendar.getInstance().apply {
    add(Calendar.HOUR_OF_DAY, 1)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * The Material 3 DatePicker stores the selected day as midnight UTC of that calendar
 * date, regardless of the device's time zone. Convert a local instant to that
 * representation so the picker highlights the same day the user is currently looking
 * at.
 */
private fun toUtcDateMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
        set(Calendar.MILLISECOND, 0)
    }
    return utc.timeInMillis
}

/**
 * Combine a UTC-midnight day from the DatePicker with a (hour, minute) chosen earlier
 * (or fallback defaults) — interpreted in the device's local time zone, which is what
 * the customer means when they say "8 sáng".
 */
private fun combineDateUtcWithLocalTime(
    dateUtcMillis: Long,
    existingLocalMillis: Long?,
    fallbackHour: Int,
    fallbackMinute: Int
): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateUtcMillis
    }
    val year = utcCal.get(Calendar.YEAR)
    val month = utcCal.get(Calendar.MONTH)
    val day = utcCal.get(Calendar.DAY_OF_MONTH)

    val (hour, minute) = if (existingLocalMillis != null) {
        val local = Calendar.getInstance().apply { timeInMillis = existingLocalMillis }
        local.get(Calendar.HOUR_OF_DAY) to local.get(Calendar.MINUTE)
    } else fallbackHour to fallbackMinute

    return Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * Replace the time-of-day on a local epoch instant.
 */
private fun combineDateLocalWithTime(dateMillis: Long, hour: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
