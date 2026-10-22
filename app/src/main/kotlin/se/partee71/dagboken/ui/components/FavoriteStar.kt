package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.IconSize
import se.partee71.ui.components.AppIconButton
import se.partee71.ui.components.AppMenuItem

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

/**
 * Favoritraden i en långtrycks- eller åtgärdsmeny (HEM-11, FAV-3, FAV-12): samma text och stjärnikon som [FavoriteStar] –
 * "Markera X som favorit" med tom stjärna, "Ta bort X som favorit" med ifylld.
 */
@Composable
fun favoriteMenuItem(name: String, favorite: Boolean, onToggle: () -> Unit): AppMenuItem = AppMenuItem(
    stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add, name),
    onToggle,
    if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star
)

/** Den lilla fyllda stjärnan efter titeln på en stjärnmärkt post (FAV-12); TalkBack läser "Favorit". */
@Composable
fun FavoriteMark(modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.ic_star_filled),
        contentDescription = stringResource(R.string.favorite_on),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(IconSize.pill)
    )
}
