package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import se.partee71.dagboken.R

/**
 * Stjärnan som markerar [name] som favorit – radens enda inline-direktkontroll (NFR-17): ifylld när
 * [favorite], annars tom. TalkBack läser vad ett tryck gör ("Markera Promenad som favorit" / "Ta bort
 * Promenad som favorit") och läget som `stateDescription` ("Favorit"/"Inte favorit"). Listor (SET-5, SET-9) och vid behov-medicinerna i Mediciner (MEDF-3, SET-10).
 */
@Composable
fun FavoriteStar(name: String, favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add, name)
    val state = stringResource(if (favorite) R.string.favorite_on else R.string.favorite_off)
    AppIconButton(if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star, description, onToggle, modifier.semantics { stateDescription = state })
}
