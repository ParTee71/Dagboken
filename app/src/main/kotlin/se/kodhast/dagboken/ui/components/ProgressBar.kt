package se.kodhast.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.ui.components.AppProgress

/**
 * Framstegsraden (HEM-18) med Dagbokens texter: android-ui:s [AppProgress] – "4 av 9 klara" över ett spår som fylls
 * animerat i teal, solgult med mörk kontur och "9 av 9 · allt klart" när allt är klart (färgerna ur temat, HEM-19,
 * DSN-4). TalkBack läser "x av y klara" och, när allt är klart, "9 av 9 klara, allt klart". [celebrate] = false håller
 * raden i teal med "9 av 9 klara" även när allt är klart – belöningsläget gäller bara dagens datum.
 */
@Composable
fun ProgressBar(done: Int, total: Int, modifier: Modifier = Modifier, celebrate: Boolean = true) {
    val label = stringResource(R.string.progress_format)
    val doneLabel = stringResource(R.string.progress_done_format)
    val doneDescription = stringResource(R.string.progress_done_description)
    AppProgress(
        done,
        total,
        label = { count, all -> label.format(count, all) },
        doneLabel = { all -> doneLabel.format(all, all) },
        modifier = modifier,
        celebrate = celebrate,
        doneDescription = { all -> doneDescription.format(all, all) }
    )
}
