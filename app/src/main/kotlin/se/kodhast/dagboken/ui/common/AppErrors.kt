package se.kodhast.dagboken.ui.common

import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.data.common.asDataError
import se.kodhast.ui.common.ErrorMapper
import se.kodhast.ui.common.UiError

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
