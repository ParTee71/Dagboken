package se.partee71.dagboken.domain.model

enum class NoteTarget {
    ACTIVITY, SCREENING, MEDICATION, RECEPT, FAVORIT, EVENT, SJUKDOM_EPISOD, SJUKDOM_INCHECKNING;

    companion object {
        /**
         * Aktiviteter och screeningar delar tabell och skiljs bara på [Aktivitet.type],
         * så anteckningsmålet måste härledas ur typen. Samlat här i stället för att
         * upprepas på varje anropsplats.
         */
        fun forAktivitet(type: String): NoteTarget =
            if (type == "screening") SCREENING else ACTIVITY
    }
}
