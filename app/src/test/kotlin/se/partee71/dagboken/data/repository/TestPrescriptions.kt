package se.partee71.dagboken.data.repository

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.firestore.FirestoreSyncStatus

/** [DefaultDoseRepository] i test – en gång för alla testklasser; [clock] avgör "nu" (avbockning, framtid). */
fun testDoses(collections: CollectionFactory, zone: TimeZone, clock: Clock = FixedClock()): DefaultDoseRepository =
    DefaultDoseRepository(collections, clock) { zone }

/**
 * [DefaultPrescriptionRepository] i test – en gång för alla testklasser. Dossynken efter en sparning
 * körs i [background] (standard: direkt, så att doserna syns när `save` returnerat) och dess fel hamnar
 * i [sync] som i appen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun testPrescriptions(
    collections: CollectionFactory,
    doses: DoseRepository,
    zone: TimeZone,
    clock: Clock = FixedClock(),
    background: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    sync: FirestoreSyncStatus = FirestoreSyncStatus(background),
): DefaultPrescriptionRepository = DefaultPrescriptionRepository(collections, doses, clock, background, sync) { zone }
