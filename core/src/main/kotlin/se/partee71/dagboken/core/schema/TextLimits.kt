package se.partee71.dagboken.core.schema

/**
 * Längsta text som sparas: samma tak i `firestore.rules` (`maxShort`/`maxLong`, hålls
 * lika av tools/db/test/schema.test.mjs) och i textfälten, så att det man skriver alltid går att
 * spara. Generöst tilltagna – de skyddar användarens kvot, inte formuläret. 3.x hade inga
 * längdgränser: konverteraren validerar varje genererat dokument mot dessa tak och stoppar med en
 * rapport om något inte ryms – den kapar aldrig (ARKITEKTUR.md → Migrering, OMB-3).
 */
object TextLimits {
    /** Namn, styrka och andra enradiga fält. */
    const val SHORT = 200

    /** Anteckningar och förklaringar – det enda 3.x-fältet som rimligen kan vara långt. */
    const val LONG = 5000
}
