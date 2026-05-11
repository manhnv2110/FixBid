package com.example.fixbid.data.repository

import com.example.fixbid.data.remote.dto.BidDto
import com.example.fixbid.data.remote.dto.toDto
import com.example.fixbid.data.remote.supabase.Tables
import com.example.fixbid.domain.model.Bid
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.repository.BidRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class BidRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : BidRepository {

    override suspend fun placeBid(bid: Bid): Resource<Bid> = runCatching {
        val result = client.postgrest[Tables.BIDS]
            .insert(bid.toDto()) { select(Columns.ALL) }
            .decodeSingle<BidDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Đặt thầu thất bại") }

    override suspend fun getBidsForBooking(bookingId: String): Resource<List<Bid>> =
        runCatching {
            val result = client.postgrest[Tables.BIDS]
                .select(Columns.ALL) {
                    filter { eq("booking_id", bookingId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<BidDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải danh sách thầu") }

    override suspend fun getMyBids(workerId: String): Resource<List<Bid>> =
        runCatching {
            val result = client.postgrest[Tables.BIDS]
                .select(Columns.ALL) {
                    filter { eq("worker_id", workerId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<BidDto>()
            Resource.Success(result.map { it.toDomain() })
        }.getOrElse { Resource.Error(it.message ?: "Lỗi tải danh sách bid") }

    override suspend fun acceptBid(bidId: String): Resource<Bid> = runCatching {
        val result = client.postgrest[Tables.BIDS]
            .update(buildJsonObject { put("status", "accepted") }) {
                filter { eq("id", bidId) }
                select(Columns.ALL)
            }
            .decodeSingle<BidDto>()
        Resource.Success(result.toDomain())
    }.getOrElse { Resource.Error(it.message ?: "Chấp nhận thầu thất bại") }

    override suspend fun rejectBid(bidId: String): Resource<Unit> = runCatching {
        client.postgrest[Tables.BIDS]
            .update(buildJsonObject { put("status", "rejected") }) {
                filter { eq("id", bidId) }
            }
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Lỗi từ chối bid") }

    override suspend fun withdrawBid(bidId: String): Resource<Unit> = runCatching {
        client.postgrest[Tables.BIDS]
            .update(buildJsonObject { put("status", "withdrawn") }) {
                filter { eq("id", bidId) }
            }
        Resource.Success(Unit)
    }.getOrElse { Resource.Error(it.message ?: "Rút bid thất bại") }

    override fun observeBidsForBooking(bookingId: String): Flow<List<Bid>> {
        val channel = client.realtime.channel("bids_$bookingId")
        return channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = Tables.BIDS
                filter("booking_id", FilterOperator.EQ, bookingId)
            }
            .map {
                (getBidsForBooking(bookingId) as? Resource.Success)?.data ?: emptyList()
            }
    }
}