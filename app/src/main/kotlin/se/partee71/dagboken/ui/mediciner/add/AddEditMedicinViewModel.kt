package se.partee71.dagboken.ui.mediciner.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.Timestamps
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase
import se.partee71.dagboken.ui.formatTime
import se.partee71.dagboken.ui.mediciner.CooldownWarning
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class MedicinForm(
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "mg",
    val tidpunkt: String = "Morgon",
    val anteckning: String = "",
    val datum: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val tid: String = formatTime(LocalTime.now()),
    val tagen: Boolean = false,
)

@HiltViewModel
class AddEditMedicinViewModel @Inject constructor(
    private val repo: MedicinerRepository,
    private val noteRepo: NoteRepository,
    private val cooldownUseCase: CheckCooldownUseCase,
    private val limitUseCase: CheckDailyLimitUseCase,
) : ViewModel() {

    private val _form = MutableStateFlow(MedicinForm())
    val form: StateFlow<MedicinForm> = _form.asStateFlow()
    private var editingMedicin: Medicin? = null
    private var favorit: Favorit? = null

    private var originalForm = _form.value
    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _cooldownWarning = MutableStateFlow<CooldownWarning?>(null)
    val cooldownWarning: StateFlow<CooldownWarning?> = _cooldownWarning.asStateFlow()

    private val _blockedMessage = MutableStateFlow<String?>(null)
    val blockedMessage: StateFlow<String?> = _blockedMessage.asStateFlow()

    // Incremented on a successful save so the screen can navigate back — used instead
    // of an unconditional "save then pop" because a favorit efterhandslogg (MED-16)
    // can legitimately be blocked (cooldown/dagsgräns) and must keep the screen open.
    private val _saveCompleted = MutableStateFlow(0)
    val saveCompleted: StateFlow<Int> = _saveCompleted.asStateFlow()

    /** True when editing an existing tagning (Historik → tryck på en post, MED-15). */
    fun isEditingExisting(): Boolean = editingMedicin != null

    /** Receptgenererade doser redigerar aldrig namn/tidpunkt här — de hör till receptet. */
    fun isFromRecept(): Boolean = editingMedicin?.receptId != null

    /** True i "logga i efterhand"-läget, öppnat från en favorits långtrycksmeny (MED-16). */
    fun isFromFavorit(): Boolean = favorit != null

    private fun setCleanForm(form: MedicinForm) {
        originalForm = form
        _form.value = form
        _isDirty.value = false
    }

    fun updateForm(update: MedicinForm.() -> MedicinForm) {
        _form.value = _form.value.update()
        _isDirty.value = _form.value != originalForm
    }

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val m = repo.getMedicinById(id) ?: return@launch
            editingMedicin = m
            val note = noteRepo.observe(NoteTarget.MEDICATION, id).first()
            setCleanForm(
                MedicinForm(
                    namn       = m.namn,
                    dos        = m.dos,
                    enhet      = m.enhet,
                    tidpunkt   = m.tidpunkt,
                    anteckning = note,
                    datum      = m.datum,
                    tid        = m.tagenTid ?: m.tid,
                    tagen      = m.tagen,
                ),
            )
        }
    }

    /** Prefyller formuläret från en favorit för en efterhandsloggad vid behov-dos (MED-16). */
    fun loadForFavorit(favoritId: String) {
        viewModelScope.launch {
            val f = repo.getFavoritById(favoritId) ?: return@launch
            favorit = f
            val note = noteRepo.observe(NoteTarget.FAVORIT, favoritId).first()
            setCleanForm(
                _form.value.copy(
                    namn       = f.namn,
                    dos        = f.dos,
                    enhet      = f.enhet,
                    tidpunkt   = "Vid behov",
                    anteckning = note,
                    tagen      = true,
                ),
            )
        }
    }

    fun dismissCooldownWarning() { _cooldownWarning.value = null }
    fun dismissBlockedMessage()  { _blockedMessage.value = null }

    fun save(force: Boolean = false) {
        viewModelScope.launch {
            val f = _form.value
            val original = editingMedicin
            val fav = favorit
            val timestamp = Timestamps.of(f.datum, f.tid)

            if (fav != null && !force) {
                val takenOnDate = repo.countDailyDoses(f.datum, fav.namn)
                if (limitUseCase.limitReached(fav.maxDoserPerDag, takenOnDate)) {
                    _blockedMessage.value = "Max ${fav.maxDoserPerDag} doser/dag nådda för ${fav.namn}"
                    return@launch
                }
                val lastTaken = repo.getLastTakenBefore(fav.namn, timestamp)
                val remaining = cooldownUseCase.remainingHours(
                    fav.namn, fav.minTidMellan, lastTaken, Instant.parse(timestamp),
                )
                if (remaining != null) {
                    _cooldownWarning.value = CooldownWarning(fav, remaining)
                    return@launch
                }
            }

            // En receptgenererad dos redigerar aldrig sin schemalagda tid (`tid`) eller
            // sitt namn/tidpunktsslot här — bara den faktiska tagningen (`tagenTid`,
            // dos, enhet, tagen-status). Flyttas den till ett annat datum kan den inte
            // behålla sitt stabila slot-id (recept_{id}_{datum}_{tidpunkt}) — annars
            // skriver nästa ensureEntriesForDate-körning (för det nya ELLER det gamla
            // datumet) över den redigerade raden. I stället sparas den om under ett
            // nytt id (receptId behålls, ursprungsraden tas bort), vilket låter
            // ursprungsdagens schema generera en ny, otagen dos vid nästa körning.
            val isReceptDose = original?.receptId != null
            val movedReceptDose = isReceptDose && f.datum != original!!.datum

            val medicin = when {
                isReceptDose -> original!!.copy(
                    id        = if (movedReceptDose) UUID.randomUUID().toString() else original.id,
                    timestamp = if (movedReceptDose) Timestamps.of(f.datum, original.tid) else original.timestamp,
                    datum     = f.datum,
                    dos       = f.dos.trim(),
                    enhet     = f.enhet,
                    tagen     = f.tagen,
                    tagenTid  = if (f.tagen) f.tid else null,
                )
                original != null -> original.copy(
                    namn      = f.namn.trim(),
                    dos       = f.dos.trim(),
                    enhet     = f.enhet,
                    tidpunkt  = f.tidpunkt,
                    datum     = f.datum,
                    tid       = f.tid,
                    timestamp = timestamp,
                    tagen     = f.tagen,
                    tagenTid  = if (f.tagen) f.tid else null,
                )
                fav != null -> Medicin(
                    id        = UUID.randomUUID().toString(),
                    timestamp = timestamp,
                    datum     = f.datum,
                    tid       = f.tid,
                    namn      = f.namn.trim(),
                    dos       = f.dos.trim(),
                    enhet     = f.enhet,
                    tidpunkt  = f.tidpunkt,
                    tagen     = true,
                    tagenTid  = f.tid,
                )
                else -> {
                    val today  = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val nowTid = formatTime(LocalTime.now())
                    Medicin(
                        id        = UUID.randomUUID().toString(),
                        timestamp = Timestamps.of(today, nowTid),
                        datum     = today,
                        tid       = nowTid,
                        namn      = f.namn.trim(),
                        dos       = f.dos.trim(),
                        enhet     = f.enhet,
                        tidpunkt  = f.tidpunkt,
                        tagen     = false,
                    )
                }
            }

            if (movedReceptDose) {
                repo.deleteMedicin(original!!)
                noteRepo.delete(NoteTarget.MEDICATION, original.id)
            }
            repo.saveMedicin(medicin)
            noteRepo.save(NoteTarget.MEDICATION, medicin.id, f.anteckning.trim())

            _cooldownWarning.value = null
            _blockedMessage.value  = null
            originalForm = f
            _isDirty.value = false
            _saveCompleted.value += 1
        }
    }
}
