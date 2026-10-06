package se.partee71.dagboken.core.time

import kotlinx.datetime.TimeZone

/**
 * Dagbokens fasta tidszon, Europe/Stockholm. Används där ett ögonblick ska bli samma dag på alla
 * enheter oavsett enhetens tidszon: 3.x-tider i konverteraren (ARKITEKTUR.md → Migrering, punkt 1)
 * och receptets skapandedag som intervallankare (REC-4). Allt som gäller "nu" och användarens dag tar
 * i stället enhetens zon som parameter.
 */
val HOME_ZONE: TimeZone = TimeZone.of("Europe/Stockholm")
