package se.partee71.dagboken.core.engine.health

import se.partee71.dagboken.core.engine.knownCount

// Hälsa idag och Idags veckotrender (HEM-15, HEM-17, HLS-6, HLS-8) – det som visas räknas här, inte i appen.
// Klockdatan persisteras aldrig (HLS-5); allt är härlett i stunden ur `DailyHealth` och `HealthHistory`.

/** Så många dagar med värde krävs för en trend (TRD-13, HEM-17) – diagrammens tomma läge har samma gräns. */
const val MIN_TREND_POINTS = 2

/**
 * Veckans värden som trendrad på Idag (HEM-17): [points] när minst [MIN_TREND_POINTS] dagar har värde, annars
 * `null` – då utelämnas raden i stället för att visa ett tomt diagram.
 */
fun trendOrNull(points: List<Float?>): List<Float?>? = points.takeIf { knownCount(it) >= MIN_TREND_POINTS }
