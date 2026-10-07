package se.partee71.dagboken.core.model

import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.time.datesBetween

// Klockdatan (§19 HLS) som rena datatyper – läses live ur Health Connect och persisteras **aldrig**
// (HLS-5): ingen codec, ingen samling, ingen plats i exporten. Portade från 3.x `domain/model/HealthData.kt`
// med kotlinx-datum och kotlin.time i stället för java.time.

/**
 * Sömnstadierna för en natt (HLS-8): djup, REM, lätt och vaken tid. Samsung Health skriver stadier
 * inuti sömnsessionen; saknas de (natt utan klocka på armen) är fälten `null` och sömnen visas bara
 * som längd.
 */
data class SleepStages(
    val deep: Duration? = null,
    val rem: Duration? = null,
    val light: Duration? = null,
    val awake: Duration? = null,
) {
    val isEmpty: Boolean get() = deep == null && rem == null && light == null && awake == null
}

/**
 * Ett dygns hälsodata (HLS-12) – samma mått som Hälsa idag, knutna till ett datum så att de går att rita
 * över tid. Alla mått är nullbara: ett dygn utan mätning ger **ingen** datapunkt, aldrig en nolla – en
 * nolla vore ett påstående om ett dygn som aldrig mättes och skulle dra ner varje trendlinje.
 *
 * Nattens värden ([sleepDuration], [sleepStages], [sleepMidpointSdMinutes]) hör till det dygn sömnen **slutade** – en
 * natt över midnatt hamnar på morgonens datum (HLS-12). [sleepMidpointSdMinutes] är sömnens regelbundenhet för natten:
 * spridningen i mittpunkten över de 14 dygn som slutar med den (HLS-10, HLS-13); `null` utan natt eller med för få
 * nätter i fönstret.
 */
data class DailyHealth(
    val date: LocalDate,
    val steps: Long? = null,
    val restingHeartRate: Long? = null,
    val heartRateAvg: Long? = null,
    val sleepDuration: Duration? = null,
    val sleepStages: SleepStages = SleepStages(),
    val sleepMidpointSdMinutes: Double? = null,
    val exerciseSessions: Int = 0,
    val exerciseDuration: Duration? = null,
    val activeEnergyKcal: Double? = null,
    val distanceMeters: Double? = null,
    val oxygenSaturationAvg: Double? = null,
) {
    /** Ingen mätning alls det här dygnet – en lucka i varje diagram. */
    val isEmpty: Boolean
        get() = steps == null && restingHeartRate == null && heartRateAvg == null &&
            sleepDuration == null && sleepStages.isEmpty && exerciseDuration == null &&
            activeEnergyKcal == null && distanceMeters == null && oxygenSaturationAvg == null
}

/**
 * Hälsohistoriken för en period (HLS-12): **en post per dygn, äldst först**, utan hål i datumen. Dygn
 * utan mätning finns med som tomma [DailyHealth], så att en lucka i datan blir en lucka i kurvan – inte
 * en hoptryckt x-axel.
 */
data class HealthHistory(val days: List<DailyHealth> = emptyList()) {
    val dates: List<LocalDate> get() = days.map { it.date }

    /** Ett måtts dygnsvärden som diagramserie – `null` där måttet saknas den dagen. */
    fun series(value: (DailyHealth) -> Number?): List<Float?> = days.map { day -> value(day)?.toFloat() }

    /** Minst ett dygn har någon mätning. */
    val hasAnyData: Boolean get() = days.any { !it.isEmpty }

    companion object {
        /** Perioden [from]…[to] utan en enda mätning – en tom post per dygn (klockan okopplad, HLS-4). */
        fun empty(from: LocalDate, to: LocalDate): HealthHistory = HealthHistory(datesBetween(from, to).map { DailyHealth(it) })

        /**
         * Historiken för [from]…[to] ur [measured] (ett eller inget värde per datum): varje dygn får sin
         * mätning eller en tom post, i datumordning – samma form oavsett hur glest klockan mätt.
         */
        fun of(from: LocalDate, to: LocalDate, measured: Map<LocalDate, DailyHealth>): HealthHistory =
            HealthHistory(datesBetween(from, to).map { date -> measured[date]?.copy(date = date) ?: DailyHealth(date) })
    }
}
