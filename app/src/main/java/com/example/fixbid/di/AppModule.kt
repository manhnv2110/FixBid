package com.example.fixbid.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-level Hilt module.
 * UserPreferencesDataStore and ProfileRepository are provided automatically
 * via their @Inject constructors.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
