package se.partee71.dagboken.core.model

/**
 * Läkemedelsformen på ett recept eller en vid behov-medicin (REC-1, FAV-1). Saknas den är formen ej
 * angiven (`null` på modellen) – 3.x hade inget sådant fält. Ett okänt lagrat värde (från en nyare app)
 * bevaras som text och skrivs tillbaka oförändrat (`unknownForm`), som receptets okända tidpunkter.
 */
enum class MedicineForm(override val wire: String) : WireEnum {
    TABLET("tablet"),
    CAPSULE("capsule"),
    LIQUID("liquid"),
    POWDER("powder"),
    INHALER("inhaler"),
    DROPS("drops"),
    PATCH("patch"),
    OTHER("other"),
}

/** "Alvedon 500 mg": namn och styrka med de tomma delarna utelämnade – visningsnamnet för recept, medicin och dos. */
internal fun medicineTitle(name: String, strength: String): String =
    listOf(name, strength).map(String::trim).filter(String::isNotEmpty).joinToString(" ")
