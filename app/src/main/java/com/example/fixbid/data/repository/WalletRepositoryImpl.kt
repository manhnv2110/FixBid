package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.WalletDto
import com.example.fixbid.data.remote.dto.WalletTransactionDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.data.remote.supabase.liveFlow
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.Wallet
import com.example.fixbid.domain.model.WalletTransaction
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
}
