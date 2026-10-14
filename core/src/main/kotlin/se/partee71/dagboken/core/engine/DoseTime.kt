package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.ReminderSettings

// Dosens klockslag i schemat (REC-6, NOT-18, MED-4) – en regel, här, för Idag, veckosammanfattningen och dosformuläret.

/**
 * REC-6, NOT-18: klockslaget dosen hör till i schemat. En **planerad eller överhoppad receptdos i schemat**
 * ([isScheduledPrescription]) följer tidpunktens klockslag i inställningarna ([ReminderSettings.timeOf]) – också när det
 * ändrats efter att dosen skapades, så att en lagrad dos aldrig behöver skrivas om och klockslaget inte hoppar när dosen
 * hoppas över (och tas igen i formuläret). En tagen dos och en vid behov-, extra- eller engångsdos behåller sitt lagrade
 * klockslag ([Dose.plannedTime]; en tagen visas med sin tagningstid, MED-14); saknas det gäller tidpunktens.
 */
fun Dose.scheduledTime(reminders: ReminderSettings): LocalTime =
    if (status != DoseStatus.TAKEN && isScheduledPrescription) reminders.timeOf(slot) else plannedTime ?: reminders.timeOf(slot)
