package se.kodhast.dagboken.ui.common

import kotlinx.coroutines.flow.Flow
import se.kodhast.dagboken.core.model.Archivable
import se.kodhast.ui.components.ArchiveActions

/** [ArchiveActions.visible] för modellerna i `:core`: aktiva först, arkiverade sist och bara när de visas (SET-5). */
fun <T : Archivable> ArchiveActions.visible(source: Flow<List<T>>): Flow<List<T>> = visible(source) { it.archived }
