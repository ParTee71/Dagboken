package se.partee71.dagboken.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Medicin
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HomeUiState(
    val todayMediciner: List<Medicin> = emptyList(),
    val screeningPoints: List<Float> = emptyList(),
    val lastAktivitet: Aktivitet? = null,
    val tagenCount: Int = 0,
    val googleEmail: String? = null,
    val googlePhotoUrl: String? = null,
    val googleDisplayName: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val aktiviteterRepo: AktiviteterRepository,
    private val medicinerRepo: MedicinerRepository,
    private val authRepo: FirebaseAuthRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { medicinerRepo.ensureTodayEntries() }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        medicinerRepo.todayFlow(),
        authRepo.authStateFlow,
    ) { today, user ->
        val last7 = aktiviteterRepo.getRecent("screening", 7)
        val lastAktivitet = aktiviteterRepo.getRecent("aktivitet", 1).firstOrNull()
        HomeUiState(
            todayMediciner    = today.sortedBy { tidpunktSortIndex(it.tidpunkt) },
            screeningPoints   = last7.map { it.energy.toFloat() },
            lastAktivitet     = lastAktivitet,
            tagenCount        = today.count { it.tagen },
            googleEmail       = user?.email,
            googlePhotoUrl    = user?.photoUrl?.toString(),
            googleDisplayName = user?.displayName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun toggleMedicinTagen(medicin: Medicin) {
        viewModelScope.launch { medicinerRepo.toggleTagen(medicin.id, !medicin.tagen) }
    }

    fun signIn(activityContext: Context) {
        viewModelScope.launch { authRepo.signInWithGoogle(activityContext) }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.clearCredentialState()
            authRepo.signOut()
        }
    }

    private fun tidpunktSortIndex(tidpunkt: String): Int {
        val order = listOf("Morgon", "Förmiddag", "Lunch", "Eftermiddag", "Kväll", "Natt", "Vid behov")
        return order.indexOf(tidpunkt).takeIf { it >= 0 } ?: order.size
    }
}

fun greeting(): String {
    return when (java.time.LocalTime.now().hour) {
        in 0..4   -> "God natt"
        in 5..11  -> "God morgon"
        in 12..16 -> "God eftermiddag"
        in 17..20 -> "God kväll"
        else      -> "God natt"
    }
}

fun formattedDate(): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale("sv", "SE"))
    return LocalDate.now().format(formatter).replaceFirstChar { it.uppercase() }
}
