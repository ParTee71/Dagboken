package se.partee71.dagboken.ui.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.plausibleBirthYears
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorLoader
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.ChoiceChips
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.LabeledGroup

/** Profilformuläret: födelseåret som det skrivs (tomt = inte angivet) och kön (HLS-11). */
data class ProfileForm(val birthYear: String = "", val sex: Sex = Sex.UNSPECIFIED) {
    fun toProfile() = Profile(birthYear = birthYear.toIntOrNull(), sex = sex)

    companion object {
        const val BIRTH_YEAR = "birthYear"

        fun of(profile: Profile) = ProfileForm(profile.birthYear?.toString().orEmpty(), profile.sex)

        /** Tomt eller ett rimligt år ([years], samma spann som sömnkvalitetens åldersnormer). */
        fun validator(years: IntRange) = Validator<ProfileForm> { form ->
            val valid = form.birthYear.isEmpty() || form.birthYear.toIntOrNull()?.let { it in years } == true
            if (valid) emptyMap() else mapOf(BIRTH_YEAR to R.string.profile_birth_year_invalid)
        }
    }
}

sealed interface ProfileEvent {
    data class BirthYearChanged(val text: String) : ProfileEvent

    data class SexChanged(val sex: Sex) : ProfileEvent

    data object Save : ProfileEvent

    data object Retry : ProfileEvent
}

/** Profil i inställningsarket (HLS-11): läser och sparar bara gruppen `profile` i `settings/app`. */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val settings: SettingsRepository,
    clock: Clock,
    timeZone: TimeZone,
) : ViewModel() {
    val editor: EditorState<ProfileForm> = EditorState(ProfileForm(), ProfileForm.validator(plausibleBirthYears(clock.todayIn(timeZone))), loading = true)
    // Det lagrade (loader.stored) är det sparningen jämför mot när bara skillnaden ska skrivas.
    private val loader = EditorLoader(editor, viewModelScope, read = { settings.get() }, project = { ProfileForm.of(it.profile) }, showInvalid = true)

    private val difference = SettingsDifference(settings) { loader.stored.value }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.BirthYearChanged ->
                editor.update(ProfileForm.BIRTH_YEAR) { it.copy(birthYear = event.text.filter(Char::isDigit).take(YEAR_DIGITS)) }
            is ProfileEvent.SexChanged -> editor.update { it.copy(sex = event.sex) }
            ProfileEvent.Save -> viewModelScope.launch {
                editor.save { form -> difference.save { it.copy(profile = form.toProfile()) } }
            }
            ProfileEvent.Retry -> loader.retry()
        }
    }

    private companion object {
        const val YEAR_DIGITS = 4
    }
}

@Composable
fun ProfileRoute(onClose: () -> Unit, viewModel: ProfileEditViewModel = hiltViewModel()) {
    val state by viewModel.editor.state.collectAsStateWithLifecycle()
    ProfileScreen(state, viewModel.editor.effects, viewModel::onEvent, onClose)
}

/** Kön i den ordning de visas: Kvinna · Man · Ej angivet. */
private val SEXES = listOf(Sex.FEMALE, Sex.MALE, Sex.UNSPECIFIED)

/** Profil: födelseår och kön på `EntityEditScreen` – Spara aktiv först när giltigt och ändrat (NFR-10). */
@Composable
fun ProfileScreen(state: EditorUiState<ProfileForm>, effects: Flow<EditorEffect>, onEvent: (ProfileEvent) -> Unit, onClose: () -> Unit) {
    EntityEditScreen(
        title = stringResource(R.string.settings_profile),
        state = state,
        effects = effects,
        onSave = { onEvent(ProfileEvent.Save) },
        onClose = onClose,
        onRetry = { onEvent(ProfileEvent.Retry) },
    ) {
        AppTextField(
            state.value.birthYear,
            { onEvent(ProfileEvent.BirthYearChanged(it)) },
            stringResource(R.string.profile_birth_year),
            error = state.errorFor(ProfileForm.BIRTH_YEAR)?.let { stringResource(it) },
            helper = stringResource(R.string.profile_birth_year_help),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        LabeledGroup(stringResource(R.string.profile_sex)) {
            ChoiceChips(SEXES, state.value.sex, { onEvent(ProfileEvent.SexChanged(it)) }, label = { stringResource(it.label()) })
        }
    }
}
