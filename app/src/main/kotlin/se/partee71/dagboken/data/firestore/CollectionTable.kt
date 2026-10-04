package se.partee71.dagboken.data.firestore

import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection

/**
 * Varje samlings namn, codec och sökväg – ett ställe, delat av den riktiga fabriken och
 * testernas fejkade fabrik, så att de bevisligen använder samma tabell. Tom tills codecarna
 * finns (etapp 2); en samling läggs till som
 * `override fun doses() = create(DoseCodec, Paths.DOSES) { Paths.doses(required(it)) }`.
 */
abstract class CollectionTable : CollectionFactory {

    /** Skapar en samling; [path] kastar [DataError.NotSignedIn] om ingen är inloggad. */
    protected abstract fun <T : Identified> create(
        codec: DocCodec<T>,
        name: String,
        path: (uid: String?) -> String,
    ): EntityCollection<T>

    /** Sökvägens uid, eller [DataError.NotSignedIn] när ingen är inloggad. */
    protected fun required(uid: String?): String = uid ?: throw DataError.NotSignedIn
}
