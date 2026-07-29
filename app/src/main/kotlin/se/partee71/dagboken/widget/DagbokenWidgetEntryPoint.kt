package se.partee71.dagboken.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.usecase.BuildScreeningAktivitetUseCase
import se.partee71.dagboken.domain.usecase.LogVidBehovDosUseCase

/**
 * Glance instantiates widget/action classes via reflection, not through Hilt's normal
 * Android entry points (Activity/Receiver/ViewModel field injection), so this is the app's
 * first [EntryPoint] — widgets reach the same repositories/usecases the rest of the app
 * uses via [EntryPointAccessors.fromApplication] instead. Shared by all three hemskärm-
 * widgets ([DagbokenWidget], [ScreeningWidget], [VidBehovWidget], #161/#162).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DagbokenWidgetEntryPoint {
    fun medicinerRepository(): MedicinerRepository
    fun aktiviteterRepository(): AktiviteterRepository
    fun preferencesRepository(): PreferencesRepository
    fun buildScreeningAktivitetUseCase(): BuildScreeningAktivitetUseCase
    fun logVidBehovDosUseCase(): LogVidBehovDosUseCase
}

fun Context.widgetEntryPoint(): DagbokenWidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, DagbokenWidgetEntryPoint::class.java)
