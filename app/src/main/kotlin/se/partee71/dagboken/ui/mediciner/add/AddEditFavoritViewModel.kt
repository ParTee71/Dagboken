package se.partee71.dagboken.ui.mediciner.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Favorit
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
) : ViewModel() {

    private val _form = MutableStateFlow(FavoritForm())
    val form: StateFlow<FavoritForm> = _form.asStateFlow()
    private var editingId: String? = null

    fun updateForm(update: FavoritForm.() -> FavoritForm) { _form.value = _form.value.update() }

    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val f = repo.getFavoritById(id) ?: return@launch
            editingId = id
            _form.value = FavoritForm(
                namn           = f.namn,
                dos            = f.dos,
                enhet          = f.enhet,
                tidpunkt       = f.tidpunkt,
                anteckning     = f.anteckning,
                minTidMellan   = f.minTidMellan,
                maxDoserPerDag = f.maxDoserPerDag,
            )
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
                anteckning     = f.anteckning.trim(),
                minTidMellan   = f.minTidMellan,
                maxDoserPerDag = f.maxDoserPerDag,
            )
            repo.saveFavorit(favorit)
        }
    }
}
