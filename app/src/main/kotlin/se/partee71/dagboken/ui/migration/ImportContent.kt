package se.partee71.dagboken.ui.migration

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.legacy.ImportFormat
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.ui.common.toMessage
import se.partee71.dagboken.ui.components.AppButton
import se.partee71.dagboken.ui.components.AppDivider
import se.partee71.dagboken.ui.components.AppLoading
import se.partee71.dagboken.ui.components.ButtonVariant
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.theme.Tone

/*
 * Importens vy (BCK-6, BCK-14, OMB-5) – **en** gång för första starten utan Room-fil (MigrationScreen, MigrationStage.Fallback)
 * och Inställningar → Export och import (regel 4). Skärmen äger ramen (rubrik, snackbar); här finns filväljaren,
 * Drive-samtycket, valraderna och ett läge per tavla i mockupen (avsnitt 16). Bara antal, aldrig innehåll.
 */

/**
 * Filväljaren (SAF, `OpenDocument` med `application/json`) och Drive-samtycket för importen. Samtycket startas när
 * [stage] blir [ImportStage.DriveConsent] (`StartIntentSenderForResult`) och svaret skickas som [ImportEvent.DriveConsent].
 * Returnerar "välj en fil", som skickar [ImportEvent.FileChosen].
 */
@Composable
fun rememberImportLaunchers(stage: ImportStage, onEvent: (ImportEvent) -> Unit): () -> Unit {
    val send by rememberUpdatedState(onEvent)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { send(ImportEvent.FileChosen(it)) }
    }
    // Samtycket startas en gång per läge – inte igen när aktiviteten återskapas (rotation) medan systemets dialog visas.
    var consentStarted by rememberSaveable { mutableStateOf(false) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        consentStarted = false
        send(ImportEvent.DriveConsent(result.resultCode == Activity.RESULT_OK))
    }
    if (stage is ImportStage.DriveConsent) {
        LaunchedEffect(stage.consent) {
            if (!consentStarted) {
                consentStarted = true
                consent.launch(IntentSenderRequest.Builder(stage.consent).build())
            }
        }
    } else if (consentStarted) {
        SideEffect { consentStarted = false }
    }
    return { pick.launch(arrayOf(JSON_MIME)) }
}

/**
 * Valen "Importera från Google Drive" och "Importera från fil" – och i första starten "Börja tomt" ([onStartEmpty]) –
 * som listrader med ikon och undertext, i den yta de står i (ett kort eller ett ark).
 */
@Composable
fun ImportChoiceRows(onDrive: () -> Unit, onFile: () -> Unit, onStartEmpty: (() -> Unit)? = null) {
    ChoiceRow(R.string.import_from_drive, R.string.import_from_drive_note, R.drawable.ic_cloud_upload, onDrive)
    AppDivider()
    ChoiceRow(R.string.import_from_file, R.string.import_from_file_note, R.drawable.ic_file, onFile)
    if (onStartEmpty != null) {
        AppDivider()
        ChoiceRow(R.string.migration_start_empty, R.string.import_start_empty_note, R.drawable.ic_book, onStartEmpty)
    }
}

/** Ett val som listrad med ikon, undertext och pil (även i Export och import); [enabled] = `false` tonar ned den. */
@Composable
internal fun ChoiceRow(@StringRes title: Int, @StringRes subtitle: Int, @DrawableRes icon: Int, onClick: () -> Unit, enabled: Boolean = true) {
    ItemRow(
        stringResource(title),
        subtitle = stringResource(subtitle),
        icon = icon,
        onClick = onClick.takeIf { enabled },
        navigates = true,
        inactive = !enabled,
    )
}

/**
 * Importens läge [stage] efter valen: läsning, granskning med `ConfirmDialog` "Importera N poster?", stopp med rapporten,
 * skrivning, klart, avvikelse och fel – samma kort som flyttens. [onPickFile] öppnar filväljaren; [onStartEmpty] finns bara i
 * första starten ("Börja tomt" utan backup på Drive) och avgör notisens text; [doneLabel]/[onDone] är knappen när importen
 * är klar ("Öppna Dagboken" i första starten, "Klar" i inställningarna). [ImportStage.Choose] visar skärmen själv.
 */
@Composable
fun ImportStageContent(
    stage: ImportStage,
    onEvent: (ImportEvent) -> Unit,
    onPickFile: () -> Unit,
    doneLabel: String,
    onDone: () -> Unit,
    onStartEmpty: (() -> Unit)? = null,
) {
    when (stage) {
        ImportStage.Choose -> Unit
        is ImportStage.Reading -> Reading(stage.source)
        // Systemets samtyckesdialog ligger överst; under den fortsätter läsningen av Drive.
        is ImportStage.DriveConsent -> Reading(LegacySource.DRIVE)
        ImportStage.NoDriveBackup -> NoDriveBackup(onEvent, onPickFile, onStartEmpty)
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
            MismatchCard(stage.before, stage.accounted, stage.mismatched, footer = null)
            RetryOrCancel(onEvent)
        }
        is ImportStage.WriteFailed -> {
            WriteFailedCard(stringResource(R.string.import_failed_title), stringResource(stage.error.toMessage()), stage.batch, stage.code, footer = null)
            RetryOrCancel(onEvent)
        }
    }
}

/** Rubriken för importens läge i första starten: valen välkomnar, resten är "Importera backup". */
@StringRes
fun importTitle(stage: ImportStage): Int = when (stage) {
    ImportStage.Choose, ImportStage.NoDriveBackup -> R.string.import_fallback_title
    else -> R.string.import_title
}

/** Raden under rubriken i första starten, där läget har en. */
@Composable
fun importSubtitle(stage: ImportStage): String? = when (stage) {
    is ImportStage.Review -> stringResource(if (stage.source == LegacySource.DRIVE) R.string.import_from_source_drive else R.string.import_from_source_file)
    is ImportStage.Writing -> stringResource(R.string.import_writing_intro)
    is ImportStage.Stopped -> stringResource(R.string.import_stopped_intro)
    else -> null
}

@Composable
private fun Reading(source: LegacySource) {
    val title = if (source == LegacySource.DRIVE) R.string.import_reading_drive else R.string.import_reading_file
    StatusCard(R.drawable.ic_database, stringResource(title)) {
        AppLoading()
        Muted(stringResource(R.string.import_reading_note))
    }
}

@Composable
private fun NoDriveBackup(onEvent: (ImportEvent) -> Unit, onPickFile: () -> Unit, onStartEmpty: (() -> Unit)?) {
    StatusCard(R.drawable.ic_warning, stringResource(R.string.import_no_drive_title)) {
        Muted(stringResource(if (onStartEmpty != null) R.string.import_no_drive else R.string.import_no_drive_settings))
    }
    Actions {
        AppButton(stringResource(R.string.import_from_file), onPickFile, Modifier.fillMaxWidth())
        if (onStartEmpty != null) {
            AppButton(stringResource(R.string.migration_start_empty), onStartEmpty, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
        } else {
            Cancel(onEvent)
        }
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

/** Granskningen: samma tabell och kort som flyttens, varningarna, "Poster med samma id ersätts" och bekräftelsen. */
@Composable
private fun Review(review: ImportStage.Review, onEvent: (ImportEvent) -> Unit) {
    val format = when (val format = review.format) {
        is ImportFormat.Legacy -> stringResource(R.string.import_source_legacy, format.version)
        is ImportFormat.Export -> stringResource(R.string.import_source_export, format.schemaVersion)
    }
    StatusCard(R.drawable.ic_database, stringResource(R.string.import_review_title), count = format, countTone = Tone.Neutral) {
        CountRows(review.counts)
    }
    WarningsNote(review.warnings)
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
            onDismiss = { onEvent(ImportEvent.Dismiss) },
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
