package se.partee71.dagboken.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.OptionsRepository

/**
 * Listan [kind] för ett formulärs val (typer och symptom, AKT-1, AKT-6, SCR-2, SET-9) – en gång för alla formulär:
 * följs medan formuläret visas ([sharing]), så att en ändring i Listor syns direkt; tom tills den lästs och vid
 * läsfel (ett tillägg som inte får fälla formuläret, `withFallback`).
 */
fun OptionsRepository.choices(
    kind: OptionKind,
    scope: CoroutineScope,
    sharing: SharingStarted = screenSharing(forgetWhenStopped = false)
): StateFlow<List<Option>> = observe(kind).distinctUntilChanged().withFallback(emptyList()).stateIn(scope, sharing, emptyList())

/**
 * Namnen i listan [kind] per id, arkiverade med (SET-11) – för det som visar en posts alternativ med dess nuvarande
 * namn; vid läsfel tomt (`withFallback`), så att en post visas utan namnet i stället för att skärmen faller.
 */
fun OptionsRepository.names(kind: OptionKind): Flow<Map<String, String>> = observe(kind).withFallback(emptyList()).map { all -> all.associate { it.id to it.name } }.distinctUntilChanged()
