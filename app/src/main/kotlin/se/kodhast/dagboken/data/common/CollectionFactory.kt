package se.kodhast.dagboken.data.common

import se.kodhast.dagboken.core.model.Activity
import se.kodhast.dagboken.core.model.Checkin
import se.kodhast.dagboken.core.model.Dose
import se.kodhast.dagboken.core.model.Event
import se.kodhast.dagboken.core.model.IllnessEpisode
import se.kodhast.dagboken.core.model.Option
import se.kodhast.dagboken.core.model.Prescription
import se.kodhast.dagboken.core.model.PrnMedicine
import se.kodhast.dagboken.core.model.Screening
import se.kodhast.dagboken.core.model.Settings

/**
 * Skapar alla samlingar (ARKITEKTUR.md → Datamodell) – sökvägarna finns på ett ställe (`Paths`),
 * namn och codec i `CollectionTable`.
 */
interface CollectionFactory {
    /** `settings` – bara dokumentet `Settings.ID` används (DAT-11). */
    fun settings(): EntityCollection<Settings>

    fun options(): EntityCollection<Option>

    fun prescriptions(): EntityCollection<Prescription>

    fun prnMedicines(): EntityCollection<PrnMedicine>

    fun doses(): EntityCollection<Dose>

    fun screenings(): EntityCollection<Screening>

    fun activities(): EntityCollection<Activity>

    fun events(): EntityCollection<Event>

    fun illnessEpisodes(): EntityCollection<IllnessEpisode>

    /** Incheckningarna under episoden [episodeId]. */
    fun checkins(episodeId: String): EntityCollection<Checkin>
}
