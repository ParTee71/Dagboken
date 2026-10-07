package se.partee71.dagboken.data.health

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Klockdatan och behörighetsflödet ur Health Connect (#243, §19 HLS): [HealthConnectRepository] och
 * [HealthPermissionsImpl] ovanpå källan [HealthConnectClientSource]. Trender → Klocka och Hälsa idag på Idag (#241)
 * bygger på samma kontrakt (HLS-5, HLS-12, HLS-14); `UnavailableHealthRepository`/`UnavailableHealthPermissions`
 * finns kvar som standardsvar i test.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds abstract fun source(source: HealthConnectClientSource): HealthConnectSource

    @Binds abstract fun health(repository: HealthConnectRepository): HealthRepository

    @Binds abstract fun permissions(permissions: HealthPermissionsImpl): HealthPermissions
}
