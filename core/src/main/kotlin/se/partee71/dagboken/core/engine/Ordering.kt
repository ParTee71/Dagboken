package se.partee71.dagboken.core.engine

import se.partee71.dagboken.core.model.Identified

/**
 * Namn utan hänsyn till skiftläge, sedan id – den enda ordningen för mediciner, recept och doser med samma namn
 * (lika namn ordnas på id, så att ordningen alltid är densamma).
 */
fun <T : Identified> nameThenId(name: (T) -> String): Comparator<T> = compareBy<T, String>(String.CASE_INSENSITIVE_ORDER, name).thenBy { it.id }
