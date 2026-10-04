package se.partee71.dagboken.data.firestore

import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.core.schema.CheckinCodec
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.EventCodec
import se.partee71.dagboken.core.schema.IllnessEpisodeCodec
import se.partee71.dagboken.core.schema.OptionCodec
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.core.schema.PrnMedicineCodec
import se.partee71.dagboken.core.schema.ScreeningCodec
import se.partee71.dagboken.core.schema.SettingsCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection

/**
 * Varje samlings namn, codec och sökväg – ett ställe, delat av den riktiga fabriken och
 * testernas fejkade fabrik, så att de bevisligen använder samma tabell.
 */
abstract class CollectionTable : CollectionFactory {

    /** Skapar en samling; [path] kastar [DataError.NotSignedIn] om ingen är inloggad. */
    protected abstract fun <T : Identified> create(
        codec: DocCodec<T>,
        name: String,
        path: (uid: String?) -> String,
    ): EntityCollection<T>

    override fun settings() = create(SettingsCodec, Paths.SETTINGS) { Paths.settings(required(it)) }

    override fun options() = create(OptionCodec, Paths.OPTIONS) { Paths.options(required(it)) }

    override fun prescriptions() = create(PrescriptionCodec, Paths.PRESCRIPTIONS) { Paths.prescriptions(required(it)) }

    override fun prnMedicines() = create(PrnMedicineCodec, Paths.PRN_MEDICINES) { Paths.prnMedicines(required(it)) }

    override fun doses() = create(DoseCodec, Paths.DOSES) { Paths.doses(required(it)) }

    override fun screenings() = create(ScreeningCodec, Paths.SCREENINGS) { Paths.screenings(required(it)) }

    override fun activities() = create(ActivityCodec, Paths.ACTIVITIES) { Paths.activities(required(it)) }

    override fun events() = create(EventCodec, Paths.EVENTS) { Paths.events(required(it)) }

    override fun illnessEpisodes() = create(IllnessEpisodeCodec, Paths.ILLNESS_EPISODES) { Paths.illnessEpisodes(required(it)) }

    override fun checkins(episodeId: String) =
        create(CheckinCodec, Paths.CHECKINS) { Paths.checkins(required(it), episodeId) }

    /** Sökvägens uid, eller [DataError.NotSignedIn] när ingen är inloggad. */
    protected fun required(uid: String?): String = uid ?: throw DataError.NotSignedIn
}
