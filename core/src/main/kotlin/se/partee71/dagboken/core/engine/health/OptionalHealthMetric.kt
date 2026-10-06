package se.partee71.dagboken.core.engine.health

// De valfria måtten (HLS-8, HLS-9, HLS-14) – portade från 3.x `HealthConnectRepository.kt`. Behörighetssträngarna
// hör till Health Connect-källan; här finns bara måtten som domänbegrepp och urvalet av dem som saknar åtkomst.

/**
 * De **valfria** måtten (HLS-8, HLS-9) som domänbegrepp, så att skärmen kan säga *vilka mått* som saknar
 * åtkomst (HLS-14) utan att känna till Health Connects behörighetsnamn. Sömnstadierna saknas med flit: de
 * ryms i kärnans `READ_SLEEP` och kan inte nekas separat.
 */
enum class OptionalHealthMetric { EXERCISE, ACTIVE_ENERGY, DISTANCE, OXYGEN_SATURATION, BLOOD_PRESSURE, HISTORY }

/**
 * De mått i [permissions] (mått → behörighet) vars behörighet saknas i [granted] (HLS-14). Kärnbehörigheter
 * i [granted] påverkar inte svaret. Kan behörigheterna inte läsas ska anroparen inte fråga alls – hellre
 * ingen varning än en som bygger på en gissning.
 */
fun missingMetrics(granted: Set<String>, permissions: Map<OptionalHealthMetric, String>): Set<OptionalHealthMetric> =
    permissions.filterValues { it !in granted }.keys
