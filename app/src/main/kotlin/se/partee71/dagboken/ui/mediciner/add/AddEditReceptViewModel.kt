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
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.Recept
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/** Hur receptets period anges (REC-7). */
enum class PeriodMode { TILLS_VIDARE, LANGD, SLUTDATUM }

data class ReceptForm(
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "mg",
    val tidpunkter: List<String> = listOf("Morgon"),
    val upprepning: String = "dagligen",
    val dagar: List<Int> = emptyList(),
    val intervalDagar: Int = 1,
    val anteckning: String = "",
    val aktiv: Boolean = true,
    val skapad: String = LocalDate.now().toString(),
    val startDatum: String = LocalDate.now().toString(),
    val periodMode: PeriodMode = PeriodMode.TILLS_VIDARE,
    val langdDagar: Int = 14,
    val slutDatumVal: String = LocalDate.now().plusDays(13).toString(),
    val dosperioder: List<Dosperiod> = emptyList(),
)

/** Perioden som formuläret beskriver — null betyder tills vidare. */
fun ReceptForm.resolvedSlutDatum(): String? = when (periodMode) {
    PeriodMode.TILLS_VIDARE -> null
    PeriodMode.SLUTDATUM    -> slutDatumVal.takeIf { it.isNotBlank() }
    PeriodMode.LANGD        -> parseDate(startDatum)
        ?.plusDays((langdDagar.coerceAtLeast(1) - 1).toLong())
        ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

private fun parseDate(value: String?): LocalDate? =
    value?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }

/** Valideringsfel som blockerar spara — null = giltigt formulär. */
enum class ReceptFormError { SLUT_FORE_START, DOSPERIOD_SLUT_FORE_START, DOSPERIOD_OVERLAPP, DOSPERIOD_UTAN_DOS }

fun ReceptForm.validate(): ReceptFormError? {
    val start = parseDate(startDatum)
    val slut  = parseDate(resolvedSlutDatum())
    if (start != null && slut != null && slut.isBefore(start)) return ReceptFormError.SLUT_FORE_START

    if (dosperioder.any { it.dos.isBlank() }) return ReceptFormError.DOSPERIOD_UTAN_DOS

    val ranges = dosperioder.map { p ->
        val pStart = parseDate(p.startDatum) ?: LocalDate.MIN
        val pSlut  = parseDate(p.slutDatum) ?: slut ?: LocalDate.MAX
        if (pSlut.isBefore(pStart)) return ReceptFormError.DOSPERIOD_SLUT_FORE_START
        pStart to pSlut
    }.sortedBy { it.first }

    ranges.zipWithNext().forEach { (a, b) ->
        if (!b.first.isAfter(a.second)) return ReceptFormError.DOSPERIOD_OVERLAPP
    }
    return null
}

@HiltViewModel
class AddEditReceptViewModel @Inject constructor(
    private val repo: MedicinerRepository,
    private val noteRepo: NoteRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(ReceptForm())
    val form: StateFlow<ReceptForm> = _form.asStateFlow()
    private var editingId: String? = null

    private var originalForm = _form.value
    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _validationError = MutableStateFlow<ReceptFormError?>(null)
    val validationError: StateFlow<ReceptFormError?> = _validationError.asStateFlow()

    private fun setCleanForm(form: ReceptForm) {
        originalForm = form
        _form.value = form
        _isDirty.value = false
        _validationError.value = form.validate()
    }

    private fun publish(form: ReceptForm) {
        _form.value = form
        _isDirty.value = form != originalForm
        _validationError.value = form.validate()
    }

    fun updateForm(update: ReceptForm.() -> ReceptForm) { publish(_form.value.update()) }

    /** Sätts när [loadForEdit] inte hittar posten — skärmen stänger sig då i stället för
     *  att låta nästa "Spara" skapa en ny post av misstag. */
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val r = repo.getReceptById(id) ?: run { _loadFailed.value = true; return@launch }
            editingId = id
            val note = noteRepo.observe(NoteTarget.RECEPT, id).first()
            val start = r.startDatum.ifBlank { r.skapad }
            val slut  = r.slutDatum
            setCleanForm(
                ReceptForm(
                    namn          = r.namn,
                    dos           = r.dos,
                    enhet         = r.enhet,
                    tidpunkter    = r.tidpunkter,
                    upprepning    = r.upprepning,
                    dagar         = r.dagar,
                    intervalDagar = r.intervalDagar,
                    anteckning    = note,
                    aktiv         = r.aktiv,
                    skapad        = r.skapad,
                    startDatum    = start,
                    periodMode    = if (slut == null) PeriodMode.TILLS_VIDARE else PeriodMode.SLUTDATUM,
                    langdDagar    = dagarMellan(start, slut) ?: 14,
                    slutDatumVal  = slut ?: LocalDate.now().plusDays(13).toString(),
                    dosperioder   = r.dosperioder,
                ),
            )
        }
    }

    private fun dagarMellan(start: String, slut: String?): Int? {
        val s = parseDate(start) ?: return null
        val e = parseDate(slut) ?: return null
        return (ChronoUnit.DAYS.between(s, e) + 1).toInt().takeIf { it >= 1 }
    }

    // Läser och skriver formuläret i ett svep — två snabba tryck kunde annars läsa
    // samma utgångsvärde och tappa den ena ändringen.
    fun toggleTidpunkt(t: String) = updateForm {
        val cur = tidpunkter
        copy(tidpunkter = if (t in cur) cur.takeIf { it.size <= 1 } ?: (cur - t) else cur + t)
    }

    fun toggleDag(dag: Int) = updateForm {
        copy(dagar = (if (dag in dagar) dagar - dag else dagar + dag).sorted())
    }

    fun setPeriodMode(mode: PeriodMode) { publish(_form.value.copy(periodMode = mode)) }

    /** Ny dosperiod (REC-9) — startar dagen efter den sista befintliga, eller på periodstart. */
    fun addDosperiod() {
        val f = _form.value
        val start = f.dosperioder
            .mapNotNull { parseDate(it.slutDatum) }
            .maxOrNull()
            ?.plusDays(1)
            ?: parseDate(f.startDatum)
            ?: LocalDate.now()
        val nyPeriod = Dosperiod(
            id         = UUID.randomUUID().toString(),
            startDatum = start.format(DateTimeFormatter.ISO_LOCAL_DATE),
            slutDatum  = start.plusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE),
            dos        = f.dos,
            enhet      = f.enhet,
        )
        publish(f.copy(dosperioder = f.dosperioder + nyPeriod))
    }

    fun updateDosperiod(id: String, update: Dosperiod.() -> Dosperiod) {
        val updated = _form.value.dosperioder.map { if (it.id == id) it.update() else it }
        publish(_form.value.copy(dosperioder = updated))
    }

    fun removeDosperiod(id: String) {
        publish(_form.value.copy(dosperioder = _form.value.dosperioder.filterNot { it.id == id }))
    }

    /**
     * [onDone] anropas först när skrivningen är klar. Skärmen navigerade tidigare
     * tillbaka direkt efter anropet, vilket rensade ViewModel:en och därmed kunde
     * cancellera viewModelScope mitt i Room-skrivningen (NFR-12).
     */
    fun save(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val f = _form.value
            if (f.validate() != null) return@launch
            val recept = Recept(
                id            = editingId ?: UUID.randomUUID().toString(),
                namn          = f.namn.trim(),
                dos           = f.dos.trim(),
                enhet         = f.enhet,
                tidpunkter    = f.tidpunkter,
                upprepning    = f.upprepning,
                dagar         = f.dagar,
                intervalDagar = f.intervalDagar,
                aktiv         = f.aktiv,
                skapad        = f.skapad,
                startDatum    = f.startDatum,
                slutDatum     = f.resolvedSlutDatum(),
                dosperioder   = f.dosperioder.map { it.copy(dos = it.dos.trim()) },
            )
            repo.saveRecept(recept)
            noteRepo.save(NoteTarget.RECEPT, recept.id, f.anteckning.trim())
            originalForm = f
            _isDirty.value = false
            onDone()
        }
    }
}
