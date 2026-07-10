package se.partee71.dagboken.ui.mediciner.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.NoteTarget
import java.util.UUID
import javax.inject.Inject

data class FavoritForm(
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "mg",
    val tidpunkt: String = "Vid behov",
    val anteckning: String = "",
    val minTidMellan: Int = 4,
    val maxDoserPerDag: Int = 0,
)

@HiltViewModel
class AddEditFavoritViewModel @Inject constructor(
    private val repo: MedicinerRepository,
    private val noteRepo: NoteRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(FavoritForm())
    val form: StateFlow<FavoritForm> = _form.asStateFlow()
    private var editingId: String? = null

    // Not editable from this screen (toggled via Settings/long-press menu instead) —
    // carried through so save() doesn't silently reset an existing favorite's status.
    private var editingIsFavorite: Boolean = false

    private var originalForm = _form.value
    val isDirty: StateFlow<Boolean> = form
        .map { it != originalForm }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun markClean() { originalForm = _form.value }

    fun updateForm(update: FavoritForm.() -> FavoritForm) { _form.value = _form.value.update() }

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val f = repo.getFavoritById(id) ?: return@launch
            editingId = id
            editingIsFavorite = f.isFavorite
            val note = noteRepo.observe(NoteTarget.FAVORIT, id).first()
            val loaded = FavoritForm(
                namn           = f.namn,
                dos            = f.dos,
                enhet          = f.enhet,
                tidpunkt       = f.tidpunkt,
                anteckning     = note,
                minTidMellan   = f.minTidMellan,
                maxDoserPerDag = f.maxDoserPerDag,
            )
            originalForm = loaded
            _form.value = loaded
        }
    }

    fun save() {
        viewModelScope.launch {
            val f = _form.value
            val favorit = Favorit(
                id             = editingId ?: UUID.randomUUID().toString(),
                namn           = f.namn.trim(),
                dos            = f.dos.trim(),
                enhet          = f.enhet,
                tidpunkt       = f.tidpunkt,
                minTidMellan   = f.minTidMellan,
                maxDoserPerDag = f.maxDoserPerDag,
                isFavorite     = editingIsFavorite,
            )
            repo.saveFavorit(favorit)
            noteRepo.save(NoteTarget.FAVORIT, favorit.id, f.anteckning.trim())
            markClean()
        }
    }
}
