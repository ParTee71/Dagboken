package se.partee71.dagboken.ui.log

import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.ui.common.nowInMinutes
import se.partee71.dagboken.ui.common.EditorSheet
import se.partee71.dagboken.ui.common.EditorSheetState
import se.partee71.dagboken.ui.common.choices

/**
 * Måendearkets rubrik (HEM-5), tagen när arket öppnas: tillfället och – en annan dag än idag – dagen.
 * Symptomvalen följer symptomlistan medan arket är öppet ([ScreeningSheet.symptomOptions]).
 */
data class ScreeningSheetInfo(val occasion: Occasion?, val date: LocalDate?)

/**
 * Måendearket (HEM-5, HEM-8b, SCR-1, SCR-2, SCR-6) – en gång för de två ställen som öppnar det: Idags Mående-kort och
 * plusknappens tillfällesväljare (och Dagbokens måendepost). Formuläret är den delade [EditorSheet]; sparat →
 * [onSaved] (meddelandet "Mående sparat", SCR-3). [scope] är ViewModelns och [sharing] hur symptomlistan följs.
 */
class ScreeningSheet(
    scope: CoroutineScope,
    private val screenings: ScreeningRepository,
    options: OptionsRepository,
    private val clock: Clock,
    private val zone: Provider<TimeZone>,
    sharing: SharingStarted,
    onSaved: () -> Unit,
) {
    /**
     * Symptomlistan (Listor) för arkets val (SCR-2) – följs medan arket är öppet, så att en sen lista ger
     * symptomsteget; tom tills den lästs och vid läsfel.
     */
    val symptomOptions: StateFlow<List<Option>> = options.choices(OptionKind.SYMPTOM, scope, sharing)

    private val sheet = EditorSheet<Screening, ScreeningSheetInfo>(scope, onSaved)

    /** Arket när det är öppet. */
    val current: StateFlow<EditorSheetState<Screening, ScreeningSheetInfo>?> = sheet.current

    /**
     * HEM-5, HEM-8b, SCR-6: en ny logg för [occasion] på [date] (`null` = idag) – också en tidigare dag. Dag och
     * klockslag ur **en** läsning av klockan: idag är klockslaget nu (också om midnatt passerat innan skärmen hunnit
     * följa med), en tidigare dag tillfällets påminnelsetid [reminder] (eller standardtiden; 3.x tog alltid "nu").
     */
    fun log(occasion: Occasion, date: LocalDate?, reminder: LocalTime?) {
        val now = clock.nowInMinutes(zone.get())
        val day = date ?: now.date
        val time = if (day == now.date) now.time else reminder ?: occasion.defaultTime
        open(null, screenings.new(day, occasion).copy(time = time), now.date)
    }

    /** SCR-1: en sparad logg för ändring – från Idags tillfällesrad eller Dagbok (HIST-3). */
    fun edit(screening: Screening) = open(screening, screening, clock.now().toLocalDateTime(zone.get()).date)

    // Id:t och skapandetiden hör till loggen, inte formuläret.
    fun changeEnergy(energy: Int) = sheet.update(ENERGY) { it.copy(energy = energy) }

    fun changeStress(stress: Int) = sheet.update(STRESS) { it.copy(stress = stress) }

    fun changeSymptoms(symptoms: List<SymptomScore>) = sheet.update(SYMPTOMS) { it.copy(symptoms = symptoms) }

    /** SCR-1: ny logg som den är, ändrad fältvis (`ScreeningRepository.save`). */
    fun save() = sheet.save(screenings::save)

    fun close() = sheet.close()

    /**
     * Öppnar arket med [value] ([loaded] = den sparade loggen). Rubriken tas nu, så att arket står sig medan skärmen
     * bakom laddar om: dagen står i den när den inte är [today] (samma klockläsning som gav loggens dag).
     */
    private fun open(loaded: Screening?, value: Screening, today: LocalDate) {
        sheet.open(loaded, value, ScreeningSheetInfo(value.occasion, value.date?.takeIf { it != today }))
    }

    private companion object {
        /** Arkets fält (rörda när de ändrats, `EditorState`). */
        const val ENERGY = "energy"
        const val STRESS = "stress"
        const val SYMPTOMS = "symptoms"
    }
}
