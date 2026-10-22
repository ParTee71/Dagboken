package se.partee71.dagboken.ui.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.toMessage
import se.partee71.dagboken.ui.theme.Neutral
import se.partee71.dagboken.ui.theme.Positive
import se.partee71.ui.components.AppButton
import se.partee71.ui.components.AppLoading
import se.partee71.ui.components.ButtonVariant
import se.partee71.ui.components.ConfirmDialog
import se.partee71.ui.components.ItemRow
import se.partee71.ui.theme.Tone

/*
 * Importens vy (BCK-6) i Inställningar → Export och import. Skärmen äger ramen (rubrik, snackbar); här finns
 * filväljaren, valraden och ett läge per tavla i mockupen (avsnitt 16). Bara antal, aldrig innehåll.
 */

/**
 * Filväljaren för importen (SAF, `OpenDocument` med `application/json`). Returnerar "välj en fil", som skickar
 * [ImportEvent.FileChosen].
 */
@Composable
fun rememberFilePicker(onEvent: (ImportEvent) -> Unit): () -> Unit {
    val send by rememberUpdatedState(onEvent)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { send(ImportEvent.FileChosen(it)) }
    }
    return { pick.launch(arrayOf(JSON_MIME)) }
}

/** Ett val som listrad med ikon, undertext och pil (raderna i Export och import); [enabled] = `false` tonar ned den. */
@Composable
internal fun ChoiceRow(@StringRes title: Int, @StringRes subtitle: Int, @DrawableRes icon: Int, onClick: () -> Unit, enabled: Boolean = true) {
    ItemRow(
        stringResource(title),
        subtitle = stringResource(subtitle),
        icon = icon,
        onClick = onClick.takeIf { enabled },
        navigates = true,
        inactive = !enabled
    )
}

/**
 * Importens läge [stage] efter valen: läsning, granskning med `ConfirmDialog` "Importera N poster?", stopp med rapporten,
 * skrivning, klart, avvikelse och fel. [onPickFile] öppnar filväljaren; [doneLabel]/[onDone] är knappen när importen är
 * klar ("Klar"). [ImportStage.Choose] visar skärmen själv.
 */
@Composable
fun ImportStageContent(stage: ImportStage, onEvent: (ImportEvent) -> Unit, onPickFile: () -> Unit, doneLabel: String, onDone: () -> Unit) {
    when (stage) {
        ImportStage.Choose -> Unit

        ImportStage.Reading -> Reading()

        ImportStage.NotABackup -> {
            ProblemCard(stringResource(R.string.import_not_a_backup_title), listOf(stringResource(R.string.import_not_a_backup)))
            ChooseOtherOrCancel(onEvent, onPickFile)
        }

        is ImportStage.ReadFailed -> ReadFailed(stage, onEvent, onPickFile)

        is ImportStage.Review -> Review(stage, onEvent)

        is ImportStage.Stopped -> {
            StoppedCard(stringResource(R.string.import_stopped_title), stage.report)
            ChooseOtherOrCancel(onEvent, onPickFile)
        }

        is ImportStage.Writing -> {
            WritingCard(stringResource(R.string.import_writing_title), stage.before, stage.done)
            Actions { AppButton(doneLabel, {}, Modifier.fillMaxWidth(), enabled = false) }
        }

        is ImportStage.Done -> {
            DoneCard(stringResource(R.string.import_done_title), stage.before, stage.after)
            Note(stringResource(R.string.import_done_note), R.drawable.ic_check, Tone.Positive)
            Actions { AppButton(doneLabel, onDone, Modifier.fillMaxWidth()) }
        }

        is ImportStage.Mismatch -> {
            MismatchCard(stage.before, stage.accounted, stage.mismatched)
            RetryOrCancel(onEvent)
        }

        is ImportStage.WriteFailed -> {
            WriteFailedCard(stringResource(R.string.import_failed_title), stringResource(stage.error.toMessage()), stage.batch, stage.code)
            RetryOrCancel(onEvent)
        }
    }
}

@Composable
private fun Reading() {
    StatusCard(R.drawable.ic_database, stringResource(R.string.import_reading_file)) {
        AppLoading()
        Muted(stringResource(R.string.import_reading_note))
    }
}

@Composable
private fun ReadFailed(failed: ImportStage.ReadFailed, onEvent: (ImportEvent) -> Unit, onPickFile: () -> Unit) {
    val reason = failed.error?.let { stringResource(it.toMessage()) } ?: stringResource(R.string.import_read_failed_file)
    ProblemCard(stringResource(R.string.import_read_failed_title), listOf(reason))
    Actions {
        AppButton(stringResource(R.string.retry), { onEvent(ImportEvent.Retry) }, Modifier.fillMaxWidth())
        AppButton(stringResource(R.string.import_choose_other), onPickFile, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        Cancel(onEvent)
    }
}

/** Granskningen: tabellen med antal per typ, "Poster med samma id ersätts" och bekräftelsen. */
@Composable
private fun Review(review: ImportStage.Review, onEvent: (ImportEvent) -> Unit) {
    val format = stringResource(R.string.import_source_export, review.schemaVersion)
    StatusCard(R.drawable.ic_database, stringResource(R.string.import_review_title), count = format, countTone = Tone.Neutral) {
        CountRows(review.counts)
    }
    val replaces = if (review.replaced > 0) {
        pluralStringResource(R.plurals.import_replaces_count, review.replaced, countText(review.replaced))
    } else {
        stringResource(R.string.import_replaces)
    }
    Note(replaces, R.drawable.ic_info, Tone.Neutral)
    Actions {
        AppButton(stringResource(R.string.import_action), { onEvent(ImportEvent.Import) }, Modifier.fillMaxWidth(), enabled = review.canImport)
        Cancel(onEvent)
    }
    if (review.confirming) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.import_confirm_title, review.total, countText(review.total)),
            message = stringResource(R.string.import_confirm_body),
            confirmLabel = stringResource(R.string.import_confirm),
            onConfirm = { onEvent(ImportEvent.Confirm) },
            onDismiss = { onEvent(ImportEvent.Dismiss) }
        )
    }
}

@Composable
private fun ChooseOtherOrCancel(onEvent: (ImportEvent) -> Unit, onPickFile: () -> Unit) {
    Actions {
        AppButton(stringResource(R.string.import_choose_other), onPickFile, Modifier.fillMaxWidth())
        Cancel(onEvent)
    }
}

@Composable
private fun RetryOrCancel(onEvent: (ImportEvent) -> Unit) {
    Actions {
        AppButton(stringResource(R.string.retry), { onEvent(ImportEvent.Retry) }, Modifier.fillMaxWidth())
        Cancel(onEvent)
    }
}

/** "Avbryt": tillbaka till valen. */
@Composable
private fun Cancel(onEvent: (ImportEvent) -> Unit) {
    AppButton(stringResource(R.string.cancel), { onEvent(ImportEvent.Reset) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
}
