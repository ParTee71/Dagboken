package se.partee71.dagboken.core.time

/** Tidsenheternas storlek – en gång för :core och appen (regel 4). */
const val SECONDS_PER_MINUTE = 60
const val MINUTES_PER_HOUR = 60
const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
const val SECONDS_PER_DAY = MINUTES_PER_DAY * SECONDS_PER_MINUTE
const val DAYS_PER_WEEK = 7
