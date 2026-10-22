package se.partee71.dagboken.core.time

import kotlinx.datetime.TimeZone

/**
 * Dagbokens fasta tidszon, Europe/Stockholm. Används där ett ögonblick ska bli samma dag på alla
 * enheter oavsett enhetens tidszon: receptets skapandedag som intervallankare (REC-4), också för de
 * 3.x-tider som 3.x-konverteraren (borttagen i 4.9.0) skrev i den. Allt som gäller "nu" och användarens dag tar
 * i stället enhetens zon som parameter.
 */
val HOME_ZONE: TimeZone = TimeZone.of("Europe/Stockholm")
