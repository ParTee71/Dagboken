package se.partee71.dagboken.data.repository

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DailyGate
import se.partee71.dagboken.data.common.MemoryDayMarks
import se.partee71.dagboken.data.firestore.FirestoreSyncStatus

/** [DefaultDoseRepository] i test – en gång för alla testklasser; [clock] avgör "nu" (avbockning, framtid). */
fun testDoses(collections: CollectionFactory, zone: TimeZone, clock: Clock = FixedClock()): DefaultDoseRepository = DefaultDoseRepository(collections, clock) { zone }

/**
 * [DefaultPrescriptionRepository] i test – en gång för alla testklasser. Dossynken efter en sparning
 * körs i [background] (standard: direkt, så att doserna syns när `save` returnerat) och dess fel hamnar
 * i [sync] som i appen. Städningens dagsgrind ([gate], #304) är som standard en egen per instans med tom lagring –
 * en ny instans städar alltså på nytt; dela en grind för att efterlikna appens enda instans. Tidpunkternas klockslag för
 * nya doser läses ur [settings] (NOT-18) – som standard samma samlingar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun testPrescriptions(
    collections: CollectionFactory,
    doses: DoseRepository,
    zone: TimeZone,
    clock: Clock = FixedClock(),
    background: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    sync: FirestoreSyncStatus = FirestoreSyncStatus(background),
    gate: DailyGate = DailyGate(MemoryDayMarks(), TestUserScope()),
    settings: SettingsRepository = DefaultSettingsRepository(collections)
): DefaultPrescriptionRepository = DefaultPrescriptionRepository(collections, doses, clock, background, sync, { zone }, gate, settings)
