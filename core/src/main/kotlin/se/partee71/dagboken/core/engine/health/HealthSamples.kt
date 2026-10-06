package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Hälsomotorns indata (§19 HLS) – källoberoende poster utan Health Connect-SDK. Health Connect-källan (#243)
// översätter sina `Record`-typer hit; allt därefter är ren logik i `:core` som går att testa utan Android.
// Klockdatan persisteras aldrig (HLS-5): typerna har ingen codec och ingen samling.

/** En post ur en källa – [origin] är källans paketnamn (`dataOrigin`), grunden för per-källa-valet (HLS-2). */
sealed interface HealthSample {
    val origin: String
}

/** Ett tidsintervall, halvöppet `[start, end)` så att angränsande intervall inte överlappar. */
interface TimeSpan {
    val start: Instant
    val end: Instant
}

val TimeSpan.duration: Duration get() = end - start

/** True om [time] ligger i intervallet – starten räknas med, slutet inte. */
operator fun TimeSpan.contains(time: Instant): Boolean = time >= start && time < end

/** Ett bart tidsfönster, t.ex. ett sömnfönster som ska sållas bort ur vilopulsskattningen (HLS-7). */
data class TimeWindow(override val start: Instant, override val end: Instant) : TimeSpan

/**
 * Sömnfönstren för en period som uppslagsindex: överlappande fönster (t.ex. samma natt från två källor) slås
 * ihop till ett, sorteras, och en tidpunkt slås upp med tvådelning – O(log n) per prov i stället för att
 * jämföra varje pulsprov med varje fönster i perioden. Halvöppet `[start, end)` som [TimeSpan]; fönster som
 * bara möts i en punkt slås inte ihop.
 */
class SleepWindows(spans: List<TimeSpan>) {
    private val merged: List<TimeWindow> = buildList {
        for (span in spans.filter { it.end > it.start }.sortedBy { it.start }) {
            val last = lastOrNull()
            if (last != null && span.start < last.end) {
                if (span.end > last.end) set(lastIndex, last.copy(end = span.end))
            } else {
                add(TimeWindow(span.start, span.end))
            }
        }
    }

    /** Det (sammanslagna) fönster som innehåller [time], eller `null` när [time] är vaken tid. */
    fun containing(time: Instant): TimeWindow? {
        // Sista fönstret som börjar senast vid [time]; fönstren är disjunkta, så bara det kan innehålla den.
        var low = 0
        var high = merged.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (merged[mid].start <= time) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return merged.getOrNull(found)?.takeIf { time in it }
    }

    operator fun contains(time: Instant): Boolean = containing(time) != null
}

/**
 * En summerbar mängd under ett intervall (steg, aktiva kalorier, sträcka). Sådana mängder skrivs av flera
 * källor för samma dygn och väljs därför per källa, aldrig summerade över källor (HLS-2, HLS-8).
 */
sealed interface SummableSample : HealthSample, TimeSpan {
    val amount: Double
}

/** Steg under ett intervall (`StepsRecord`, HLS-2). */
data class StepSample(override val origin: String, override val start: Instant, override val end: Instant, val count: Long) :
    SummableSample {
    override val amount: Double get() = count.toDouble()
}

/** Aktiva kalorier under ett intervall (`ActiveCaloriesBurnedRecord`, HLS-8). */
data class CaloriesSample(override val origin: String, override val start: Instant, override val end: Instant, val kcal: Double) :
    SummableSample {
    override val amount: Double get() = kcal
}

/** Sträcka under ett intervall (`DistanceRecord`, HLS-8). */
data class DistanceSample(override val origin: String, override val start: Instant, override val end: Instant, val meters: Double) :
    SummableSample {
    override val amount: Double get() = meters
}

/** Ett pulsprov (ett prov ur `HeartRateRecord.samples`, HLS-2). */
data class HeartRateSample(override val origin: String, val time: Instant, val bpm: Long) : HealthSample

/** En registrerad vilopuls (`RestingHeartRateRecord`, HLS-7). */
data class RestingHeartRateSample(override val origin: String, val time: Instant, val bpm: Long) : HealthSample

/** En syremättnadsmätning i procent (`OxygenSaturationRecord`, HLS-8). */
data class OxygenSample(override val origin: String, val time: Instant, val percent: Double) : HealthSample

/** En blodtrycksmätning i mmHg (`BloodPressureRecord`, HLS-8). */
data class BloodPressureSample(override val origin: String, val time: Instant, val systolic: Double, val diastolic: Double) :
    HealthSample

/** Ett träningspass (`ExerciseSessionRecord`, HLS-8) – en diskret händelse, inte en dygnssumma. */
data class ExerciseSession(override val origin: String, override val start: Instant, override val end: Instant) :
    HealthSample, TimeSpan

/**
 * Sömnstadiets typ (HLS-8) – Health Connects `SleepSessionRecord.STAGE_TYPE_*` som domänbegrepp; källan
 * översätter koderna. [SLEEPING] är ospecificerad sömn (Samsung skriver den när indelningen saknas).
 */
enum class SleepStageType { UNKNOWN, AWAKE, SLEEPING, OUT_OF_BED, LIGHT, DEEP, REM, AWAKE_IN_BED }

/** Ett stadium inne i en sömnsession. */
data class SleepStageSlice(val type: SleepStageType, override val start: Instant, override val end: Instant) : TimeSpan

/** En sömnsession (`SleepSessionRecord`, HLS-2/HLS-8) med sina stadier – tomt när klockan inte skrev några. */
data class SleepSession(
    override val origin: String,
    override val start: Instant,
    override val end: Instant,
    val stages: List<SleepStageSlice> = emptyList(),
) : HealthSample, TimeSpan

/**
 * Allt som lästs för en period, en lista per posttyp (HLS-12: varje typ läses **en gång** över perioden och
 * fördelas per dygn i efterhand). En typ utan behörighet (HLS-8) är bara en tom lista. Sömnen bör läsas med
 * startgränsen ett dygn bakåt, så att en session som korsar periodens första midnatt kommer med (HLS-7).
 */
data class HealthRecords(
    val steps: List<StepSample> = emptyList(),
    val heartRate: List<HeartRateSample> = emptyList(),
    val restingHeartRate: List<RestingHeartRateSample> = emptyList(),
    val sleep: List<SleepSession> = emptyList(),
    val exercise: List<ExerciseSession> = emptyList(),
    val calories: List<CaloriesSample> = emptyList(),
    val distance: List<DistanceSample> = emptyList(),
    val oxygen: List<OxygenSample> = emptyList(),
    val bloodPressure: List<BloodPressureSample> = emptyList(),
)

/** Dygnet [this] infaller på i [zone] – zonen är en parameter så att sommartid räknas rätt (HLS-12). */
fun Instant.dateIn(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

/**
 * Fördelar posterna per dygn efter [at] och reducerar varje dygn med [reduce] (HLS-12). Ett dygn utan
 * poster – eller där [reduce] ger `null` – saknas i kartan: en lucka, aldrig en nolla.
 */
internal inline fun <T, R : Any> List<T>.perDay(
    zone: TimeZone,
    at: (T) -> Instant,
    reduce: (List<T>) -> R?,
): Map<LocalDate, R> = groupBy { at(it).dateIn(zone) }.mapNotNull { (date, day) -> reduce(day)?.let { date to it } }.toMap()
