package com.example.fixbid.core.di

import javax.inject.Qualifier

/**
 * Qualifier for the long-lived application-scoped [kotlinx.coroutines.CoroutineScope].
 *
 * Use this scope for fire-and-forget side effects that must outlive a ViewModel
 * or use case caller — e.g. notification fan-out after a critical operation
 * has already returned a successful [com.example.fixbid.domain.model.Resource].
 *
 * The matching `@Provides` is defined in `AppModule` (Hilt singleton),
 * backed by `SupervisorJob() + Dispatchers.IO`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
