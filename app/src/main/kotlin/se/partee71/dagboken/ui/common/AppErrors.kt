package se.partee71.dagboken.ui.common

import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.asDataError
import se.partee71.ui.common.ErrorMapper
import se.partee71.ui.common.UiError

/**
 * Den enda översättningen från datalagrets fel till det som ramarna i android-ui visar ([UiError]): ett
 * [DataError] med sin text (`DataError.toMessage()`), allt annat som [DataError.Unknown]. "Försök igen"
 * erbjuds för allt utom det som inte finns längre ([DataError.NotFound]). Skickas till `EditorState`,
 * `ListLoader`, `DetailLoader` och `ArchiveActions`.
 */
object AppErrors : ErrorMapper {
    override fun map(error: Throwable): UiError {
        val data = error.asDataError()
        return UiError(data, data.toMessage(), retryable = data != DataError.NotFound)
    }

    override fun notFound(): UiError = map(DataError.NotFound)
}

/** Datalagrets fel bakom ett [UiError] från [AppErrors] – för det som avgörs på felets typ (t.ex. offline). */
val UiError.dataError: DataError get() = cause.asDataError()
