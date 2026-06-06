package com.example.fixbid.presentation.customer.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.core.utils.formatCurrencyVnd

/**
 * Bottom sheet for the customer to top up their wallet via VNPay.
 *
 * The sheet stays purely presentational — the parent ViewModel actually creates
 * the topup row and emits the VNPay URL. The user picks an amount (preset chip
 * or custom input ≥ 10,000đ), confirms, and the sheet shows a spinner until
 * the parent dismisses it after launching the browser intent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletTopupSheet(
    isSubmitting: Boolean,
    errorMessage: String?,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presets = remember { listOf(50_000L, 100_000L, 200_000L, 500_000L, 1_000_000L, 2_000_000L) }
    var amountText by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<Long?>(null) }

    val parsedAmount: Double? = amountText
        .filter(Char::isDigit)
        .takeIf { it.isNotEmpty() }
        ?.toLongOrNull()
        ?.toDouble()
    val isValid = parsedAmount != null && parsedAmount >= 10_000.0

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Nạp tiền vào ví",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Thanh toán an toàn qua VNPay",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Preset chips — material 3 filter-chip-ish, custom layout for tile style.
            Text(
                "Chọn nhanh",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                presets.forEach { value ->
                    val selected = selectedPreset == value
                    AmountPresetChip(
                        amount = value,
                        selected = selected,
                        enabled = !isSubmitting,
                        onClick = {
                            selectedPreset = value
                            amountText = value.toString()
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Hoặc nhập số tiền tùy chỉnh",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    amountText = input.filter(Char::isDigit).take(12)
                    selectedPreset = null
                },
                placeholder = { Text("Tối thiểu 10.000đ", fontSize = 14.sp) },
                trailingIcon = { Text("đ", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    parsedAmount?.let {
                        Text(
                            text = formatCurrencyVnd(it),
                            fontSize = 12.sp,
                            color = if (it >= 10_000.0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            // Error / hint banner
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        errorMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text("Hủy")
                }
                Button(
                    onClick = { parsedAmount?.let(onConfirm) },
                    enabled = isValid && !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Tiếp tục với VNPay", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountPresetChip(
    amount: Long,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant
    val container = if (selected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else
        MaterialTheme.colorScheme.surface
    val textColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatCurrencyVnd(amount.toDouble()),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
