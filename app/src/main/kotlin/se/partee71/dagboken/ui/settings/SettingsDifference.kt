package se.partee71.dagboken.ui.settings

import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.SettingsRepository

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
