package com.example.fixbid.presentation.worker.jobdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.core.utils.formatCurrencyVnd

/**
 * Worker-facing confirmation dialog for cancelling a CONFIRMED booking after
 * the customer has already paid. Confirming this dialog triggers
 * [com.example.fixbid.domain.usecase.worker.WorkerCancelBookingUseCase],
 * which atomically refunds the customer and debits the worker's pending wallet.
 *
 * The visual contract follows Requirements 2.1, 2.2, 2.3, 2.4, 2.6, 2.7. Styling
 * mirrors the destructive `RejectDialog` in `CompletionConfirmScreen` (same
 * tinted-icon header + error-tinted destructive button) so worker- and
 * customer-side confirm-then-destroy dialogs feel consistent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkerCancelDialog(
    form: CancelFormState,
    workerReceives: Long,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isSubmitting: Boolean
) {
    val isDark = isSystemInDarkTheme()
    // Same red palette `RejectDialog` and the customer "huỷ đơn" dialog use,
    // tuned per-theme so the warning still reads on a dark surface.
    val errorBoxBg = if (isDark) Color(0xFF400A0A) else Color(0xFFFFEBEE)
    val errorTint = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F)

    // Quick-pick reasons mirror the customer's cancel dialog. They give the
    // worker a one-tap way to clear the >=10 char gate, but the textfield
    // remains the source of truth so they can edit / append context.
    val presetReasons = remember {
        listOf(
            "Có việc đột xuất, không thể đến",
            "Không đủ thời gian thi công như đã hẹn",
            "Địa điểm quá xa, di chuyển không kịp",
            "Không có dụng cụ phù hợp cho công việc"
        )
    }

    AlertDialog(
        // Block dismiss while the use case is running so the worker can't
        // half-cancel the request mid-RPC.
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(errorBoxBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = errorTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Hủy đơn này?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column {
                // ── Financial impact panel (Req 2.1, 2.2) ──────────────────
                // Boxed treatment so the amount jumps out of the body copy.
                // Uses the same surface tint as the destructive icon chip.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(errorBoxBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = errorTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bạn sẽ mất số tiền này",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = errorTint
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatCurrencyVnd(workerReceives.toDouble()),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = errorTint
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Toàn bộ số tiền khách đã thanh toán sẽ được hoàn vào ví FixBid của khách.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ── Quick reason chips ─────────────────────────────────────
                Text(
                    text = "Chọn lý do hoặc tự nhập:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetReasons.forEach { reason ->
                        FilterChip(
                            selected = form.reason == reason,
                            onClick = { onReasonChange(reason) },
                            label = { Text(reason, fontSize = 12.sp) },
                            enabled = !isSubmitting,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme
                                    .primaryContainer.copy(alpha = 0.6f),
                                selectedLabelColor = MaterialTheme.colorScheme
                                    .onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Reason text field (Req 2.3, 2.4) ──────────────────────
                // The use case enforces `trim().length >= 10`; we mirror that
                // threshold in the helper text and confirm-button gate.
                OutlinedTextField(
                    value = form.reason,
                    onValueChange = onReasonChange,
                    label = { Text("Lý do hủy") },
                    placeholder = { Text("Mô tả lý do (tối thiểu 10 ký tự)...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isSubmitting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    supportingText = {
                        val length = form.trimmedReason.length
                        Text(
                            text = "$length/10 ký tự",
                            fontSize = 11.sp,
                            color = if (length < 10) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = form.errorMessage != null
                )

                // ── Server / use-case error surface ───────────────────────
                if (form.errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = form.errorMessage,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            // Destructive primary action (Req 2.6). Enabled iff reason >= 10
            // chars AND not already submitting; spinner replaces the label
            // while the use case is in flight.
            Button(
                onClick = onConfirm,
                enabled = form.isValid && !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark)
                        MaterialTheme.colorScheme.errorContainer
                    else Color(0xFFD32F2F),
                    contentColor = if (isDark)
                        MaterialTheme.colorScheme.onErrorContainer
                    else Color.White
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (isDark)
                            MaterialTheme.colorScheme.onErrorContainer
                        else Color.White
                    )
                } else {
                    Text(
                        text = "Xác nhận hủy",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        dismissButton = {
            // "Quay lại" — Req 2.7. Disabled mid-submit (Req 2.6) to prevent
            // double-tap races with the in-flight RPC + booking update.
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text(
                    text = "Quay lại",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
