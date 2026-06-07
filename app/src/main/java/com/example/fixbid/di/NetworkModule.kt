package com.example.fixbid.di

import com.example.fixbid.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_API_KEY
    ) {
        install(Auth) {
            flowType = FlowType.PKCE
            alwaysAutoRefresh = true
            // OAuth callback: fixbid://auth-callback. The matching intent
            // filter on MainActivity handles the redirect; Supabase parses
            // the URL via handleDeeplinks() to finish the PKCE exchange.
            scheme = "fixbid"
            host = "auth-callback"
        }
        install(Postgrest) {
            defaultSchema = "public"
        }
        install(Realtime)
        install(Storage)
    }
}
