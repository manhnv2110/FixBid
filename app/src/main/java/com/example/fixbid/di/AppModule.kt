package com.example.fixbid.di

import com.example.fixbid.core.di.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * App-level Hilt module.
 *
 * UserPreferencesDataStore and ProfileRepository are provided automatically
 * via their @Inject constructors.
 *
 * Provides a long-lived [CoroutineScope] qualified with [ApplicationScope]
 * for fire-and-forget side effects (e.g. notification fan-out from
 * `WorkerCancelBookingUseCase`) that must outlive the calling ViewModel.
 * Backed by [SupervisorJob] so a single child failure does not cancel siblings,
 * and [Dispatchers.IO] since these jobs are network/DB bound.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
