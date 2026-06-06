package com.example.fixbid.data.remote.dto

import com.example.fixbid.core.utils.toEpochMillis
import com.example.fixbid.domain.model.WalletWithdrawal
import com.example.fixbid.domain.model.WalletWithdrawalStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletWithdrawalDto(
    val id: String = "",
    @SerialName("user_id")              val userId: String = "",
    val amount: Double = 0.0,
    @SerialName("bank_name")            val bankName: String = "",
    @SerialName("bank_account_number")  val bankAccountNumber: String = "",
    @SerialName("bank_account_holder")  val bankAccountHolder: String = "",
    val note: String? = null,
    val status: String = "processing",
    @SerialName("rejection_reason")     val rejectionReason: String? = null,
    @SerialName("completed_at")         val completedAt: String? = null,
    @SerialName("created_at")           val createdAt: String = "",
    @SerialName("updated_at")           val updatedAt: String = ""
) {
    fun toDomain() = WalletWithdrawal(
        id                = id,
        userId            = userId,
        amount            = amount,
        bankName          = bankName,
        bankAccountNumber = bankAccountNumber,
        bankAccountHolder = bankAccountHolder,
        note              = note,
        status            = runCatching {
            WalletWithdrawalStatus.valueOf(status.uppercase())
        }.getOrDefault(WalletWithdrawalStatus.PROCESSING),
        rejectionReason   = rejectionReason,
        completedAt       = completedAt?.toEpochMillis(),
        createdAt         = createdAt.toEpochMillis(),
        updatedAt         = updatedAt.toEpochMillis()
    )
}
