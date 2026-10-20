package se.partee71.dagboken.ui.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.core.model.Archivable
import se.partee71.ui.components.ArchiveActions

/** Arkiverade dokument visas inte i listorna förrän "Visa arkiverade" finns för samlingen. */
fun <T : Archivable> Flow<List<T>>.activeOnly(): Flow<List<T>> = map { items -> items.filterNot { it.archived } }

/** [ArchiveActions.visible] för modellerna i `:core`: aktiva först, arkiverade sist och bara när de visas (SET-5). */
fun <T : Archivable> ArchiveActions.visible(source: Flow<List<T>>): Flow<List<T>> = visible(source) { it.archived }
