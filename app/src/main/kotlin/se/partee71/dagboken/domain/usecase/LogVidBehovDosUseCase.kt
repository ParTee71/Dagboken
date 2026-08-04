package se.partee71.dagboken.domain.usecase

import kotlinx.coroutines.flow.first
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.Timestamps
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.ui.formatTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

sealed interface VidBehovLogResult {
    data object Logged : VidBehovLogResult
    data class CooldownWarning(val remainingHours: Double) : VidBehovLogResult
    data object DailyLimitReached : VidBehovLogResult
}

/**
 * Loggar en vid behov-dos som tagen — samma väg som appens "Ta dos"
 * (`MedicinerViewModel.quickDos`/`forceDos`) — en enda skrivväg för vid behov-doser
 * (regel 4; delades tidigare med vid behov-widgeten, #162, borttagen i #177). Dagsgräns
 * kollas alltid; cooldown kan förbigås med [force], efter att användaren bekräftat.
 */
class LogVidBehovDosUseCase @Inject constructor(
    private val repo: MedicinerRepository,
    private val noteRepo: NoteRepository,
    private val cooldownUseCase: CheckCooldownUseCase,
    private val limitUseCase: CheckDailyLimitUseCase,
) {
    suspend fun logDose(favorit: Favorit, force: Boolean = false): VidBehovLogResult {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val takenToday = repo.countDailyDoses(today, favorit.namn)
        if (limitUseCase.limitReached(favorit.maxDoserPerDag, takenToday)) {
            return VidBehovLogResult.DailyLimitReached
        }
        if (!force) {
            val lastTaken = repo.getLastTaken(favorit.namn)
            val remaining = cooldownUseCase.remainingHours(favorit.namn, favorit.minTidMellan, lastTaken)
            if (remaining != null) {
                return VidBehovLogResult.CooldownWarning(remaining)
            }
        }

        val tid = formatTime(LocalTime.now())
        val medicinId = UUID.randomUUID().toString()
        repo.saveMedicin(
            Medicin(
                id = medicinId,
                timestamp = Timestamps.of(today, tid),
                datum = today,
                tid = tid,
                namn = favorit.namn,
                dos = favorit.dos,
                enhet = favorit.enhet,
                tidpunkt = favorit.tidpunkt,
                tagen = true,
                tagenTid = tid,
            ),
        )
        // En favorits anteckning är ett standardvärde som förs vidare till varje dos som loggas från den.
        val favoritNote = noteRepo.observe(NoteTarget.FAVORIT, favorit.id).first()
        if (favoritNote.isNotBlank()) {
            noteRepo.save(NoteTarget.MEDICATION, medicinId, favoritNote)
        }
        return VidBehovLogResult.Logged
    }
}
