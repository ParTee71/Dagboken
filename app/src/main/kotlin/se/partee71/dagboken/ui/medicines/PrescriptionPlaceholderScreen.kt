package se.partee71.dagboken.ui.medicines

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.UpcomingScreen

/**
 * Receptformuläret (REC-1…REC-13, MEDF-4, MEDF-5) byggs i #256. Tills dess öppnar kortet, menyns
 * Redigera, bannern och "Nytt recept" den här underskärmen – samma lilla topprad med tillbakapil och
 * centrerade "Snart här" som Export och import ([UpcomingScreen]), så att inget tryck är dött.
 */
@Composable
fun PrescriptionPlaceholderScreen(isNew: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    UpcomingScreen(
        stringResource(if (isNew) R.string.medicines_new_prescription else R.string.prescription_edit),
        R.drawable.ic_pill,
        stringResource(R.string.prescription_upcoming),
        modifier,
        onBack = onBack,
    )
}
