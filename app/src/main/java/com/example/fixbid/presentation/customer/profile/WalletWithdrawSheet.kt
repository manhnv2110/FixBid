package com.example.fixbid.presentation.customer.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
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
 * Bottom sheet for the customer to submit a wallet withdrawal request.
 *
 * Submitting calls `fn_request_wallet_withdrawal` server-side which atomically
 * locks the amount from `balance` into `pending_balance`. The actual bank
 * transfer happens off-app; ops eventually flips the row to COMPLETED or
 * REJECTED, refunding the lock as needed.
 *
 * Validation is mirrored in both UI (button gating) and the RPC (defensive),
 * so a stale UI cannot bypass the balance check.
 */
data class WithdrawForm(
    val amount: String = "",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankAccountHolder: String = "",
    val note: String = ""
) {
    val parsedAmount: Double?
        get() = amount.filter(Char::isDigit).takeIf { it.isNotEmpty() }
            ?.toLongOrNull()?.toDouble()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletWithdrawSheet(
    availableBalance: Double,
    isSubmitting: Boolean,
    errorMessage: String?,
    onConfirm: (
        amount: Double,
        bankName: String,
        bankAccountNumber: String,
        bankAccountHolder: String,
        note: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var form by remember { mutableStateOf(WithdrawForm()) }

    val parsed = form.parsedAmount
    val amountValid = parsed != null && parsed >= 10_000.0 && parsed <= availableBalance
    val canSubmit = amountValid &&
        form.bankName.trim().length >= 2 &&
        form.bankAccountNumber.trim().length >= 6 &&
        form.bankAccountHolder.trim().length >= 2 &&
        !isSubmitting

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
                        Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Rút tiền về tài khoản",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Số dư khả dụng: ${formatCurrencyVnd(availableBalance)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Info banner explaining the lock behaviour so the customer isn't
            // confused when their balance immediately drops on submit.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sau khi gửi yêu cầu, số tiền sẽ được tạm giữ và chuyển vào tài khoản trong 1–3 ngày làm việc.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // Amount
            FieldLabel("Số tiền muốn rút")
            OutlinedTextField(
                value = form.amount,
                onValueChange = {
                    form = form.copy(amount = it.filter(Char::isDigit).take(12))
                },
                placeholder = { Text("Tối thiểu 10.000đ", fontSize = 14.sp) },
                trailingIcon = { Text("đ", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isSubmitting,
                isError = parsed != null && (parsed < 10_000.0 || parsed > availableBalance),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    when {
                        parsed != null && parsed > availableBalance ->
                            Text(
                                "Vượt quá số dư khả dụng",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        parsed != null && parsed < 10_000.0 ->
                            Text(
                                "Số tiền tối thiểu 10.000đ",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        parsed != null ->
                            Text(
                                formatCurrencyVnd(parsed),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        else ->
                            Text(
                                "Tối đa: ${formatCurrencyVnd(availableBalance)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    }
                }
            )

            // Quick "all" chip — convenience.
            if (availableBalance >= 10_000.0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable(enabled = !isSubmitting) {
                            form = form.copy(amount = availableBalance.toLong().toString())
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Rút tất cả",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bank name
            FieldLabel("Tên ngân hàng")
            OutlinedTextField(
                value = form.bankName,
                onValueChange = { form = form.copy(bankName = it.take(60)) },
                placeholder = { Text("VD: Vietcombank, Techcombank, MB Bank...") },
                singleLine = true,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            FieldLabel("Số tài khoản")
            OutlinedTextField(
                value = form.bankAccountNumber,
                onValueChange = {
                    form = form.copy(bankAccountNumber = it.filter { c -> c.isDigit() }.take(20))
                },
                placeholder = { Text("Nhập đúng số tài khoản") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            FieldLabel("Tên chủ tài khoản")
            OutlinedTextField(
                value = form.bankAccountHolder,
                onValueChange = { form = form.copy(bankAccountHolder = it.take(80)) },
                placeholder = { Text("Tên đầy đủ trên thẻ/tài khoản") },
                singleLine = true,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            FieldLabel("Ghi chú (tuỳ chọn)")
            OutlinedTextField(
                value = form.note,
                onValueChange = { form = form.copy(note = it.take(120)) },
                placeholder = { Text("Ghi chú nội bộ cho ops, không bắt buộc") },
                minLines = 2,
                maxLines = 3,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Error
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text("Hủy")
                }
                Button(
                    onClick = {
                        val amount = parsed ?: return@Button
                        onConfirm(
                            amount,
                            form.bankName.trim(),
                            form.bankAccountNumber.trim(),
                            form.bankAccountHolder.trim(),
                            form.note.trim().ifBlank { null }
                        )
                    },
                    enabled = canSubmit,
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
                        Text("Gửi yêu cầu rút", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
