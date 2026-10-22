package se.partee71.dagboken.core.engine

// Indelningen av en skala i tre nivåer (AKT-4, AKT-5, AKT-6, SCR-1) – enda stället (regel 4). Etiketten och
// färgen per nivå väljer appen (`ui/common/EnergyLabel.kt`, `scaleLevel`).

/** Var på skalan ett värde ligger: lägsta, mittersta eller högsta delen. */
enum class ScaleZone {
    Low,
    Mid,
    High;

    /** Samma nivå sedd från andra hållet – för en skala där högre är sämre (svårighet). */
    fun mirrored(): ScaleZone = when (this) {
        Low -> High
        Mid -> Mid
        High -> Low
    }
}

/**
 * Nivån för [value] på [range]. Gränserna ligger vid 40 % och 70 % av skalan – på 0–10 betyder det 0–3, 4–6 och
 * 7–10 som i 3.x, på −10…+10 (aktivitetens energi) −10…−3, −2…+3 och +4…+10. Värden utanför skalan räknas som kanten.
 */
fun scaleZone(value: Int, range: IntRange = 0..10): ScaleZone {
    val span = (range.last - range.first).coerceAtLeast(1)
    // I tiondelar av skalan, med heltal – inga avrundningsfel precis på gränsen.
    val tenths = (value.coerceIn(range) - range.first) * TENTHS
    return when {
        tenths >= span * HIGH_FROM -> ScaleZone.High
        tenths >= span * MID_FROM -> ScaleZone.Mid
        else -> ScaleZone.Low
    }
}

private const val TENTHS = 10
private const val MID_FROM = 4
private const val HIGH_FROM = 7
