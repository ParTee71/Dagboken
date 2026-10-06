package se.partee71.dagboken.data.health

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Klockdatan: [UnavailableHealthRepository] tills Health Connect-porten (#243) byter bindningen. Ingen skärm
 * injicerar [HealthRepository] än – Trender → Klocka (#267) och Hälsa idag (#241) gör det; bindningen finns så att
 * de bygger på samma kontrakt (HLS-5, HLS-12).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds abstract fun health(repository: UnavailableHealthRepository): HealthRepository
}
