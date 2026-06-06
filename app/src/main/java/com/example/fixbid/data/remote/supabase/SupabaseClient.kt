package com.example.fixbid.data.remote.supabase

import com.example.fixbid.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object Tables {
    const val PROFILES        = "profiles"
    const val WORKER_PROFILES = "worker_profiles"
    const val BOOKINGS        = "bookings"
    const val BIDS            = "bids"
    const val PAYMENTS        = "payments"
    const val REVIEWS         = "reviews"
    const val CONVERSATIONS   = "conversations"
    const val MESSAGES        = "messages"
    const val NOTIFICATIONS   = "notifications"
    const val FCM_TOKENS      = "fcm_tokens"
    const val WALLETS              = "wallets"
    const val WALLET_TRANSACTIONS  = "wallet_transactions"
    const val WALLET_TOPUPS        = "wallet_topups"
    const val WALLET_WITHDRAWALS   = "wallet_withdrawals"
}

fun createFixBidSupabaseClient(): SupabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_API_KEY
) {
    install(Auth) {
        flowType = FlowType.PKCE
        alwaysAutoRefresh = true
    }
    install(Postgrest)
    install(Realtime)
    install(Storage)
}
