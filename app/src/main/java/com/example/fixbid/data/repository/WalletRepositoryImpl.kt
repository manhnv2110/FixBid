package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.WalletDto
import com.example.fixbid.data.remote.dto.WalletTopupDto
import com.example.fixbid.data.remote.dto.WalletTransactionDto
import com.example.fixbid.data.remote.dto.WalletWithdrawalDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.data.remote.supabase.liveFlow
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Wallet
import com.example.fixbid.domain.model.WalletTopup
import com.example.fixbid.domain.model.WalletTransaction
import com.example.fixbid.domain.model.WalletWithdrawal
import com.example.fixbid.domain.repository.WalletRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Wallet data access. Reads come straight from the `wallets` /
 * `wallet_transactions` tables (RLS guarantees the worker can only see
 * their own rows). Writes always go through the Postgres RPCs we shipped
 * in `20260603_wallets.sql` so balance + ledger row stay in sync inside
 * a single SQL transaction.
 */
class WalletRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : WalletRepository {

    override suspend fun getMyWallet(): Resource<Wallet> {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("Chưa đăng nhập")
        return getWalletByUser(userId)
    }

    override suspend fun getWalletByUser(userId: String): Resource<Wallet> = runCatching {
        // Try the cheap path first.
        val existing = client.postgrest[Tables.WALLETS]
            .select(Columns.ALL) { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<WalletDto>()
        if (existing != null) {
            return@runCatching Resource.Success(existing.toDomain())
        }

        // First-time wallet creation: ask the SQL helper which is RLS-safe
        // because it runs as SECURITY DEFINER. Returns the freshly created
        // (or pre-existing) wallet row.
        val ensured = client.postgrest.rpc(
            "fn_ensure_wallet",
            buildJsonObject { put("p_user_id", userId) }
        ).decodeAs<WalletDto>()
        Resource.Success(ensured.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không tải được ví") }

    override suspend fun getMyTransactions(limit: Int): Resource<List<WalletTransaction>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("Chưa đăng nhập")
        val rows = client.postgrest[Tables.WALLET_TRANSACTIONS]
            .select(Columns.ALL) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<WalletTransactionDto>()
        Resource.Success(rows.map { it.toDomain() })
    }.getOrElse { Resource.Error(it.message ?: "Không tải được lịch sử ví") }

    override suspend fun getMyTransactionById(id: String): Resource<WalletTransaction> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("Chưa đăng nhập")
        // RLS already restricts reads to the signed-in user's rows; the
        // explicit user_id filter is a belt-and-braces guard so a leaked id
        // can't be deep-linked into another user's ledger.
        val row = client.postgrest[Tables.WALLET_TRANSACTIONS]
            .select(Columns.ALL) {
                filter {
                    eq("id", id)
                    eq("user_id", userId)
                }
                limit(1)
            }
            .decodeSingleOrNull<WalletTransactionDto>()
            ?: return Resource.Error("Không tìm thấy giao dịch")
        Resource.Success(row.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không tải được chi tiết giao dịch") }

    override suspend fun holdEscrow(paymentId: String): Resource<Wallet> = runCatching {
        val result = client.postgrest.rpc(
            "fn_hold_escrow_to_wallet",
            buildJsonObject { put("p_payment_id", paymentId) }
        ).decodeAs<WalletDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không thể giữ tiền vào ví") }

    override suspend fun releaseEscrow(paymentId: String): Resource<Wallet> = runCatching {
        val result = client.postgrest.rpc(
            "fn_release_escrow_to_wallet",
            buildJsonObject { put("p_payment_id", paymentId) }
        ).decodeAs<WalletDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không thể chuyển tiền vào ví") }

    override suspend fun refundEscrowToCustomer(paymentId: String): Resource<Wallet> = runCatching {
        val result = client.postgrest.rpc(
            "fn_refund_escrow_to_customer",
            buildJsonObject { put("p_payment_id", paymentId) }
        ).decodeAs<WalletDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không thể hoàn tiền cho khách") }

    override fun observeMyWallet(): Flow<Wallet?> {
        val userId = client.auth.currentUserOrNull()?.id ?: return kotlinx.coroutines.flow.flowOf(null)
        val channel = client.realtime.channel("wallet_$userId")

        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = Tables.WALLETS
            filter("user_id", FilterOperator.EQ, userId)
        }.map { action ->
            runCatching {
                when (action) {
                    is PostgresAction.Insert -> action.decodeRecord<WalletDto>().toDomain()
                    is PostgresAction.Update -> action.decodeRecord<WalletDto>().toDomain()
                    else -> null
                }
            }.getOrNull()
        }
        return channel.liveFlow(changes)
    }

    // ─── Top-ups ──────────────────────────────────────────────────────────

    override suspend fun createTopup(
        amount: Double,
        vnpTxnRef: String
    ): Resource<WalletTopup> = runCatching {
        // RLS forbids direct inserts on wallet_topups — go through the
        // SECURITY DEFINER helper that captures the auth.uid() server-side.
        val result = client.postgrest.rpc(
            "fn_create_wallet_topup",
            buildJsonObject {
                put("p_amount", amount)
                put("p_vnp_txn_ref", vnpTxnRef)
            }
        ).decodeAs<WalletTopupDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không tạo được phiên nạp tiền") }

    override suspend fun creditWalletTopup(
        vnpTxnRef: String,
        transactionId: String
    ): Resource<Wallet> = runCatching {
        val result = client.postgrest.rpc(
            "fn_credit_wallet_topup",
            buildJsonObject {
                put("p_vnp_txn_ref", vnpTxnRef)
                put("p_transaction_id", transactionId)
            }
        ).decodeAs<WalletDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không cộng được tiền vào ví") }

    override suspend fun failWalletTopup(
        vnpTxnRef: String,
        responseCode: String
    ): Resource<Unit> = runCatching {
        client.postgrest.rpc(
            "fn_fail_wallet_topup",
            buildJsonObject {
                put("p_vnp_txn_ref", vnpTxnRef)
                put("p_response_code", responseCode)
            }
        )
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Không cập nhật được trạng thái nạp tiền") }

    override suspend fun getMyTopups(limit: Int): Resource<List<WalletTopup>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("Chưa đăng nhập")
        val rows = client.postgrest[Tables.WALLET_TOPUPS]
            .select(Columns.ALL) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<WalletTopupDto>()
        Resource.Success(rows.map { it.toDomain() })
    }.getOrElse { Resource.Error(it.message ?: "Không tải được lịch sử nạp tiền") }

    override suspend fun getTopupByTxnRef(vnpTxnRef: String): Resource<WalletTopup> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("Chưa đăng nhập")
        val row = client.postgrest[Tables.WALLET_TOPUPS]
            .select(Columns.ALL) {
                filter {
                    eq("vnp_txn_ref", vnpTxnRef)
                    eq("user_id", userId)
                }
                limit(1)
            }
            .decodeSingleOrNull<WalletTopupDto>()
            ?: return Resource.Error("Không tìm thấy giao dịch nạp tiền")
        Resource.Success(row.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không tải được chi tiết nạp tiền") }

    // ─── Withdrawals ──────────────────────────────────────────────────────

    override suspend fun requestWithdrawal(
        amount: Double,
        bankName: String,
        bankAccountNumber: String,
        bankAccountHolder: String,
        note: String?
    ): Resource<WalletWithdrawal> = runCatching {
        val result = client.postgrest.rpc(
            "fn_request_wallet_withdrawal",
            buildJsonObject {
                put("p_amount", amount)
                put("p_bank_name", bankName)
                put("p_bank_account_number", bankAccountNumber)
                put("p_bank_account_holder", bankAccountHolder)
                put("p_note", note)
            }
        ).decodeAs<WalletWithdrawalDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Gửi yêu cầu rút tiền thất bại") }

    override suspend fun getMyWithdrawals(limit: Int): Resource<List<WalletWithdrawal>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("Chưa đăng nhập")
        val rows = client.postgrest[Tables.WALLET_WITHDRAWALS]
            .select(Columns.ALL) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<WalletWithdrawalDto>()
        Resource.Success(rows.map { it.toDomain() })
    }.getOrElse { Resource.Error(it.message ?: "Không tải được lịch sử rút tiền") }

    override suspend fun getWithdrawalById(id: String): Resource<WalletWithdrawal> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: return Resource.Error("Chưa đăng nhập")
        val row = client.postgrest[Tables.WALLET_WITHDRAWALS]
            .select(Columns.ALL) {
                filter {
                    eq("id", id)
                    eq("user_id", userId)
                }
                limit(1)
            }
            .decodeSingleOrNull<WalletWithdrawalDto>()
            ?: return Resource.Error("Không tìm thấy yêu cầu rút tiền")
        Resource.Success(row.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Không tải được chi tiết rút tiền") }
}
