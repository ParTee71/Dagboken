package se.partee71.dagboken.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import android.content.Context
import se.partee71.dagboken.data.repository.MedicinerRepository

/**
 * Glance instantiates [DagbokenWidget]/[ToggleMedicinAction] via reflection, not through
 * Hilt's normal Android entry points (Activity/Receiver/ViewModel field injection), so this
 * is the app's first [EntryPoint] — the widget reaches the same repositories the rest of the
 * app uses via [EntryPointAccessors.fromApplication] instead.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DagbokenWidgetEntryPoint {
    fun medicinerRepository(): MedicinerRepository
}

fun Context.widgetEntryPoint(): DagbokenWidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, DagbokenWidgetEntryPoint::class.java)
