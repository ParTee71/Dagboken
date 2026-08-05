package se.partee71.dagboken.domain.model

import java.time.Duration
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sömnkvalitet (HLS-10) — ett sammanvägt 0–100-mått på nattens sömn, räknat på det
 * Health Connect faktiskt levererar från Galaxy Watch via Samsung Health.
 *
 * Måttet är ett **wellness-mått, inte en klinisk bedömning**. Validerade instrument
 * som PSQI är frågeformulär; ingen konsumentklocka har ett validerat kvalitetsindex.
 * Delkomponenterna och deras gränsvärden är däremot hämtade ur publicerad forskning,
 * se [SleepQualityKind] och `scoreSleepQuality`.
 */
data class SleepQuality(
    val score: Int,
    val components: List<SleepQualityComponent>,
    val flags: List<SleepFlag> = emptyList(),
)

/** En delkomponent med sin egen poäng och sin vikt i totalen. */
data class SleepQualityComponent(
    val kind: SleepQualityKind,
    val score: Int,
    val weight: Int,
    /** Uppmätt värde i komponentens egen enhet (timmar, procent eller minuter). */
    val measured: Double,
)

/**
 * Delkomponenterna och deras vikt i totalen.
 *
 * Vikterna följer hur **tillförlitlig** varje mätning är, inte hur mycket
 * tillverkarna brukar framhäva den. Längd, effektivitet och regelbundenhet bygger på
 * tidpunkter och sömn/vaken-avgöranden, som klockor klarar bra. Stadieklassningen är
 * betydligt svagare — i en multicenterstudie mot polysomnografi (349 114 epoker) låg
 * konsumentklockornas macro-F1 mellan 0,26 och 0,69, och djupsömn är det minst
 * tillförlitliga stadiet. Därför väger [DEEP] och [REM] tillsammans mindre än en
 * tredjedel.
 */
enum class SleepQualityKind(val weight: Int) {
    /** Total sömntid. AASM/SRS-konsensus: vuxna behöver ≥7 timmar. */
    DURATION(25),

    /** Sömneffektivitet (sömn / tid i säng). ≥85 % är den kliniska normalgränsen. */
    EFFICIENCY(20),

    /**
     * Regelbundenhet — spridningen i sömnens mittpunkt mellan nätterna. I UK Biobank
     * (60 977 personer) var sömnregelbundenhet en **starkare dödlighetsprediktor än
     * sömnlängd**: 20–48 % lägre allorsaksdödlighet i de fyra översta kvintilerna.
     */
    REGULARITY(20),

    /** Djupsömn (N3) i procent av sömntiden, jämförd med ålders- och könsnorm. */
    DEEP(15),

    /** REM-sömn i procent av sömntiden. */
    REM(12),

    /** Vaken tid efter insomnande (WASO), åldersjusterad. */
    WASO(8),
}

/** Varningar som visas vid sidan av poängen — de ska inte döljas i ett medelvärde. */
enum class SleepFlag {
    /** Medelsyremättnad under 90 % — kan tyda på sömnrelaterad andningsstörning. */
    LOW_OXYGEN_SATURATION,

    /** Sovpuls klart över den egna baslinjen — tecken på bristande återhämtning. */
    ELEVATED_SLEEPING_HEART_RATE,
}

/**
 * Rådata för en natt plus de rullande mått som kräver flera nätter. Fälten är
 * nullbara: saknas ett stadium eller för få nätter faller komponenten bort och
 * vikterna normaliseras om, i stället för att natten straffas för något som aldrig
 * mättes.
 */
data class SleepMeasurements(
    val timeInBed: Duration,
    val awake: Duration? = null,
    val deep: Duration? = null,
    val rem: Duration? = null,
    val midpointSdMinutes: Double? = null,
    val meanOxygenSaturation: Double? = null,
    val sleepingHeartRate: Long? = null,
    val baselineRestingHeartRate: Long? = null,
)

/** Skillnaden mot baslinjen (bpm) som flaggar bristande återhämtning. */
private const val ELEVATED_SLEEPING_HR_DELTA = 5

/** Medelsyremättnad under detta värde flaggas. */
private const val LOW_SPO2_THRESHOLD = 90.0

/**
 * Nollpunkten för sömneffektiviteten. Låg nog att 85 % — gränsen för kliniskt störd
 * sömn — inte hamnar högt på skalan, men inte så låg att skalan blir tandlös.
 */
private const val EFFICIENCY_ZERO_PERCENT = 75.0

/**
 * Vaken tid som ger noll poäng. En timme och en halv vaken mitt i natten är en dålig
 * natt oavsett ålder; åldersjusteringen sitter i var full poäng slutar
 * ([wasoTargetMinutes]), inte i var skalan bottnar.
 */
private const val WASO_ZERO_MINUTES = 90.0

/**
 * Räknar ut sömnkvaliteten för en natt (HLS-10).
 *
 * [age] och [sex] styr normerna för djupsömn och vaken tid — båda ändras systematiskt
 * med åldern, så en 55-årings natt får inte mätas mot en 25-årings arkitektur.
 *
 * Returnerar null om ingen komponent kunde räknas ut (t.ex. natt utan sömndata).
 *
 * Ren funktion utan SDK- eller Android-beroenden, för enhetstestning (regel 2).
 */
fun scoreSleepQuality(
    measurements: SleepMeasurements,
    age: Int,
    sex: Sex,
): SleepQuality? {
    val timeInBedMinutes = measurements.timeInBed.toMinutes().toDouble()
    if (timeInBedMinutes <= 0.0) return null

    // Utan stadier vet vi inte hur mycket av tiden i säng som var sömn; då är
    // sessionens längd det bästa estimatet av sömntiden.
    val awakeMinutes = measurements.awake?.toMinutes()?.toDouble()
    val sleepMinutes = if (awakeMinutes != null) {
        (timeInBedMinutes - awakeMinutes).coerceAtLeast(0.0)
    } else {
        timeInBedMinutes
    }
    if (sleepMinutes <= 0.0) return null

    val components = buildList {
        add(
            component(
                kind = SleepQualityKind.DURATION,
                measured = sleepMinutes / 60.0,
                // AASM/SRS: ≥7 h. Övre gränsen speglar den U-formade dödlighetskurvan.
                score = plateau(sleepMinutes / 60.0, zeroLow = 4.0, fullLow = 7.0, fullHigh = 8.5, zeroHigh = 10.0),
            ),
        )

        if (awakeMinutes != null) {
            val efficiency = sleepMinutes / timeInBedMinutes * 100.0
            add(
                component(
                    kind = SleepQualityKind.EFFICIENCY,
                    measured = efficiency,
                    // Noll vid 75 %: 85 % är gränsen för kliniskt störd sömn och ska
                    // inte hamna högt upp på skalan.
                    score = rampUp(efficiency, zeroAt = EFFICIENCY_ZERO_PERCENT, fullAt = 90.0),
                ),
            )
            add(
                component(
                    kind = SleepQualityKind.WASO,
                    measured = awakeMinutes,
                    score = rampDown(
                        awakeMinutes,
                        fullAt = wasoTargetMinutes(age),
                        zeroAt = WASO_ZERO_MINUTES,
                    ),
                ),
            )
        }

        measurements.midpointSdMinutes?.let { sd ->
            add(
                component(
                    kind = SleepQualityKind.REGULARITY,
                    measured = sd,
                    score = rampDown(sd, fullAt = 30.0, zeroAt = 90.0),
                ),
            )
        }

        measurements.deep?.let { deep ->
            val percent = deep.toMinutes() / sleepMinutes * 100.0
            val band = deepSleepBand(age, sex)
            add(
                component(
                    kind = SleepQualityKind.DEEP,
                    measured = percent,
                    // Ingen övre bestraffning: mer djupsömn än normen är inget problem.
                    // Nollpunkten ligger på halva normens nedre gräns — 0 % vore en
                    // orimlig nollpunkt, ingen med fungerande sensor hamnar där, och
                    // en natt långt under normen fick då fortfarande halva poängen.
                    score = plateau(
                        percent,
                        zeroLow = band.start / 2.0,
                        fullLow = band.start,
                        fullHigh = band.endInclusive,
                        zeroHigh = Double.MAX_VALUE,
                    ),
                ),
            )
        }

        measurements.rem?.let { rem ->
            val percent = rem.toMinutes() / sleepMinutes * 100.0
            add(
                component(
                    kind = SleepQualityKind.REM,
                    measured = percent,
                    score = plateau(percent, zeroLow = 8.0, fullLow = 18.0, fullHigh = 25.0, zeroHigh = 40.0),
                ),
            )
        }
    }

    if (components.isEmpty()) return null

    // Vikterna normaliseras över de komponenter som faktiskt gick att räkna ut, så en
    // natt utan stadier bedöms på sina egna mått i stället för att dras ner till noll.
    val totalWeight = components.sumOf { it.weight }
    val weighted = components.sumOf { it.score.toDouble() * it.weight } / totalWeight

    val flags = buildList {
        measurements.meanOxygenSaturation
            ?.takeIf { it < LOW_SPO2_THRESHOLD }
            ?.let { add(SleepFlag.LOW_OXYGEN_SATURATION) }

        val sleeping = measurements.sleepingHeartRate
        val baseline = measurements.baselineRestingHeartRate
        if (sleeping != null && baseline != null && sleeping - baseline >= ELEVATED_SLEEPING_HR_DELTA) {
            add(SleepFlag.ELEVATED_SLEEPING_HEART_RATE)
        }
    }

    return SleepQuality(
        score = weighted.roundToInt().coerceIn(0, 100),
        components = components,
        flags = flags,
    )
}

private fun component(kind: SleepQualityKind, measured: Double, score: Int) =
    SleepQualityComponent(kind = kind, score = score, weight = kind.weight, measured = measured)

/**
 * Åldersnormen för djupsömn i procent av sömntiden.
 *
 * Andelen N3 sjunker linjärt med åldern fram till ~60 år, och nedgången är brantare
 * hos män (Ohayon m.fl. 2004). Vi utgår från ~18 % hos en 20-åring och drar av per
 * decennium; efter 60 planar nedgången ut.
 */
internal fun deepSleepBand(age: Int, sex: Sex): ClosedFloatingPointRange<Double> {
    val declinePerDecade = when (sex) {
        Sex.MAN -> 2.0
        Sex.KVINNA -> 1.4
        Sex.EJ_ANGIVET -> 1.7
    }
    val decadesFrom20 = ((age.coerceIn(20, 60) - 20) / 10.0)
    val center = (18.0 - declinePerDecade * decadesFrom20).coerceAtLeast(6.0)
    return (center - 4.0).coerceAtLeast(0.0)..(center + 4.0)
}

/**
 * Åldersnormen för vaken tid efter insomnande. WASO stiger ungefär 10 minuter per
 * decennium från vuxen ålder (Ohayon m.fl. 2004) — en 55-årings uppvaknanden ska
 * inte mätas mot en 25-årings.
 */
internal fun wasoTargetMinutes(age: Int): Double =
    (20.0 + 10.0 * ((age.coerceAtLeast(30) - 30) / 10.0)).coerceAtMost(60.0)

/**
 * Spridningen i sömnens mittpunkt mellan nätterna, i minuter — appens mått på
 * regelbundenhet.
 *
 * Räknas **cirkulärt**: mittpunkterna ligger runt midnatt, och ett rakt medelvärde av
 * klockslag skulle se 23:50 och 00:10 som 12 timmar isär i stället för 20 minuter.
 * Vi lägger därför varje tidpunkt som en enhetsvektor på dygnscirkeln och använder
 * resultantens längd.
 *
 * Kräver minst [MIN_NIGHTS_FOR_REGULARITY] nätter; annars null (komponenten faller
 * bort och vikterna normaliseras om).
 */
internal fun sleepMidpointSdMinutes(midpoints: List<LocalTime>): Double? {
    if (midpoints.size < MIN_NIGHTS_FOR_REGULARITY) return null

    val radiansPerMinute = 2.0 * Math.PI / MINUTES_PER_DAY
    var sumSin = 0.0
    var sumCos = 0.0
    midpoints.forEach { time ->
        val angle = time.toSecondOfDay() / 60.0 * radiansPerMinute
        sumSin += sin(angle)
        sumCos += cos(angle)
    }
    val resultant = sqrt(sumSin * sumSin + sumCos * sumCos) / midpoints.size
    // Identiska tidpunkter ger resultant 1.0 (ln 1 = 0); flyttalsbrus kan ge en aning
    // över 1, vilket skulle göra logaritmen positiv och roten odefinierad.
    if (resultant >= 1.0) return 0.0
    if (resultant <= 0.0) return MINUTES_PER_DAY / 4.0

    val circularSdRadians = sqrt(-2.0 * ln(resultant))
    return circularSdRadians / radiansPerMinute
}

/** Färre nätter än så ger ingen meningsfull spridning. */
internal const val MIN_NIGHTS_FOR_REGULARITY = 4

private const val MINUTES_PER_DAY = 1440.0

/**
 * Poäng för ett mått med ett målband: 100 inne i bandet, linjär avtrappning ut mot
 * noll-gränserna. [zeroHigh] = [Double.MAX_VALUE] betyder att höga värden aldrig
 * straffas.
 */
internal fun plateau(
    value: Double,
    zeroLow: Double,
    fullLow: Double,
    fullHigh: Double,
    zeroHigh: Double,
): Int = when {
    value in fullLow..fullHigh -> 100
    value < fullLow -> rampUp(value, zeroAt = zeroLow, fullAt = fullLow)
    zeroHigh == Double.MAX_VALUE -> 100
    else -> rampDown(value, fullAt = fullHigh, zeroAt = zeroHigh)
}

/** Linjär poäng där högre är bättre: 0 vid [zeroAt], 100 vid [fullAt] och uppåt. */
internal fun rampUp(value: Double, zeroAt: Double, fullAt: Double): Int {
    if (abs(fullAt - zeroAt) < 1e-9) return if (value >= fullAt) 100 else 0
    val fraction = (value - zeroAt) / (fullAt - zeroAt)
    return (fraction * 100).roundToInt().coerceIn(0, 100)
}

/** Linjär poäng där lägre är bättre: 100 vid [fullAt] och nedåt, 0 vid [zeroAt]. */
internal fun rampDown(value: Double, fullAt: Double, zeroAt: Double): Int {
    if (abs(zeroAt - fullAt) < 1e-9) return if (value <= fullAt) 100 else 0
    val fraction = (zeroAt - value) / (zeroAt - fullAt)
    return (fraction * 100).roundToInt().coerceIn(0, 100)
}
