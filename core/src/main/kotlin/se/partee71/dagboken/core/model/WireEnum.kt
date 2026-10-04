package se.partee71.dagboken.core.model

/**
 * Ett enumvärde som lagras med ett stabilt namn, [wire], oberoende av Kotlin-namnet – en omdöpt
 * konstant ändrar aldrig lagrad data. Ett okänt namn (från en nyare app) läses som modellens
 * default. Ett nytt värde kräver därför höjd `schemaVersion` och samma lista i `firestore.rules`
 * (skill data-safety-backup).
 */
interface WireEnum {
    val wire: String
}
