package se.partee71.dagboken.data.health

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Klockdatan och behörighetsflödet: [UnavailableHealthRepository] och [UnavailableHealthPermissions] tills
 * Health Connect-porten (#243) byter bindningarna. Trender → Klocka och Hälsa idag på Idag (#241) bygger på samma
 * kontrakt (HLS-5, HLS-12, HLS-14).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds abstract fun health(repository: UnavailableHealthRepository): HealthRepository

    @Binds abstract fun permissions(permissions: UnavailableHealthPermissions): HealthPermissions
}
