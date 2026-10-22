package se.partee71.dagboken.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.ui.common.AppErrors
import se.partee71.ui.common.EditorLoader
import se.partee71.ui.common.EditorState
import se.partee71.ui.common.Validator

/**
 * Sparar ett inställningsformulär (Profil, Påminnelser) som bara skillnaden mot det formuläret laddades
 * med – eller senast sparade – så att en ändring från en annan enhet efter laddningen står kvar (DAT-11).
 * [loaded] är det lagrade värdet ur formulärets `EditorLoader`.
 */
internal class SettingsDifference(private val settings: SettingsRepository, private val loaded: () -> Settings?) {
    private var saved: Settings? = null

    suspend fun save(edit: (Settings) -> Settings): Result<Unit> {
        val base = saved ?: loaded() ?: return Result.failure(DataError.NotFound)
        val edited = edit(base)
        return settings.save(base, edited).onSuccess { saved = edited }
    }
}

/**
 * Ett inställningsformulär (Profil, Påminnelser) för en ViewModel: [editor] med sin läsning ur `settings/app`
 * ([project] gör formuläret av inställningarna), "Försök igen" ([retry]) och sparningen ([save]) som bara skriver
 * skillnaden ([SettingsDifference]) – [apply] lägger formuläret i inställningarna. Det lagrade är det sparningen
 * jämför mot, så en ändring från en annan enhet efter laddningen står kvar (DAT-11).
 */
internal class SettingsEditor<F>(
    settings: SettingsRepository,
    private val scope: CoroutineScope,
    initial: F,
    validator: Validator<F>,
    project: (Settings) -> F,
    showInvalid: Boolean = false,
    private val apply: (Settings, F) -> Settings
) {
    val editor: EditorState<F> = EditorState(initial, validator, AppErrors, loading = true)

    private val loader = EditorLoader(editor, scope, read = { settings.get() }, project = project, showInvalid = showInvalid)

    private val difference = SettingsDifference(settings) { loader.stored.value }

    fun retry() = loader.retry()

    fun save() {
        scope.launch { editor.save { form -> difference.save { apply(it, form) } } }
    }
}
