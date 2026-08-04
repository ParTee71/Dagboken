package se.partee71.dagboken.ui.home

import androidx.compose.runtime.Immutable
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.domain.model.Favorit
import java.time.LocalDate

/**
 * Tillstånd och handlingar som Idag-kortets inline-screening behöver.
 *
 * Finns för att korten under [HomeScreen] inte ska ta emot ViewModels: en composable som
 * tar en `AktiviteterViewModel` kan röra vad som helst i den, går inte att använda i en
 * `@Preview` och kräver hela Hilt-grafen i test. Med bindningen syns exakt vad som
 * används, och kortet kan konstrueras direkt i ett Compose-test.
 */
@Immutable
data class ScreeningFormBinding(
    val energy: Int,
    val stress: Int,
    val symptomScores: Map<String, Int>,
    val symptomOptions: List<SymptomOption>,
    /** Laddar formuläret för ett screeningtillfälle på en viss dag. */
    val onStart: (label: String, date: LocalDate) -> Unit,
    val onEnergyChange: (Int) -> Unit,
    val onStressChange: (Int) -> Unit,
    val onScoresChange: (Map<String, Int>) -> Unit,
    val onToggleSymptomFavorite: (String) -> Unit,
    /** Sparar och anropar callbacken först när skrivningen är klar (NFR-12). */
    val onSave: (onDone: () -> Unit) -> Unit,
)

/** Motsvarande bindning för vid behov-sektionen — se [ScreeningFormBinding]. */
@Immutable
data class VidBehovBinding(
    val favoriter: List<Favorit>,
    val others: List<Favorit>,
    val notes: Map<String, String>,
    val onTap: (Favorit) -> Unit,
    val onEdit: (String) -> Unit,
    val onDelete: (Favorit) -> Unit,
    val onToggleFavorite: (Favorit) -> Unit,
    val onLogEfterhand: (String) -> Unit,
) {
    /** True när det finns någon vid behov-medicin alls att visa. */
    val hasAny: Boolean get() = favoriter.isNotEmpty() || others.isNotEmpty()
}
