package se.partee71.dagboken.data.common

import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings

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
