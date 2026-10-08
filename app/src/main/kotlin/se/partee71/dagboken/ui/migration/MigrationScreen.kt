package se.partee71.dagboken.ui.migration

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.legacy.AccountCheck
import se.partee71.dagboken.data.legacy.CopyFailure
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.toMessage
import se.partee71.dagboken.ui.components.AppButton
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppLoading
import se.partee71.dagboken.ui.components.ButtonVariant
import se.partee71.dagboken.ui.components.CheckRow
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Tone

/**
 * Startdestinationen före flikarna (NAV-6): startkontrollen för kontot [uid], migreringsskärmen medan en migrering
 * erbjuds och sedan [tabs] – med `true` när användaren valt "Importera backup" (OMB-5). ViewModeln gäller kontot, så
 * att ett annat konto efter utloggning får en egen kontroll.
 */
@Composable
fun MigrationGate(uid: String, tabs: @Composable (openImport: Boolean) -> Unit) {
    val viewModel: MigrationViewModel = hiltViewModel(key = "migration-$uid")
    val state by viewModel.state.collectAsStateWithLifecycle()
    MigrationGateContent(state, viewModel::onEvent, tabs)
}

/** Vad som visas efter inloggningen: laddning under startkontrollen, migreringen eller flikarna. */
@Composable
fun MigrationGateContent(state: MigrationUiState, onEvent: (MigrationEvent) -> Unit, tabs: @Composable (openImport: Boolean) -> Unit) {
    when (val stage = state.stage) {
        // Syns bara en kort stund vid start, som inloggningens laddning.
        MigrationStage.Checking -> AppLoading()
        is MigrationStage.Closed -> tabs(stage.openImport)
        else -> MigrationScreen(state, onEvent)
    }
}

/**
 * Migreringsskärmen (OMB-2, OMB-7, OMB-8): ett läge per tavla i mockupen (avsnitt 15 och 15b), byggd av
 * [EntityDetailScreen] i flikläge (stor rubrik med en rad under) med kort, rader och banners. Kopian sparas med
 * systemets filväljare (SAF). Fel vid bekräftelsen visas som meddelande.
 */
@Composable
fun MigrationScreen(state: MigrationUiState, onEvent: (MigrationEvent) -> Unit, modifier: Modifier = Modifier) {
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        uri?.let { onEvent(MigrationEvent.CopyChosen(it)) }
    }
    val fallback = state.stage as? MigrationStage.Fallback
    val pickFile = rememberImportLaunchers(fallback?.import ?: ImportStage.Choose) { onEvent(MigrationEvent.Import(it)) }
    EntityDetailScreen(
        state = DetailUiState.Content(state.stage),
        header = null,
        onBack = null,
        modifier = modifier,
        title = stringResource(fallback?.let { importTitle(it.import) } ?: R.string.migration_title),
        subtitle = fallback?.let { importSubtitle(it.import) } ?: intro(state.stage)?.let { stringResource(it) },
        failure = state.failure,
        onErrorShown = { onEvent(MigrationEvent.ErrorShown) },
    ) { stage ->
        when (stage) {
            MigrationStage.Reading -> Reading()
            is MigrationStage.Review -> Review(stage, onEvent, onSaveCopy = { saveCopy.launch(stage.copyFileName) })
            is MigrationStage.Writing -> Writing(stage)
            is MigrationStage.Written -> Written(stage, onEvent)
            is MigrationStage.Mismatch -> Mismatch(stage, onEvent)
            is MigrationStage.WriteFailed -> WriteFailed(stage, onEvent)
            MigrationStage.Aborting -> Aborting()
            is MigrationStage.Stopped -> Stopped(stage, onEvent)
            is MigrationStage.WrongVersion -> WrongVersion(onEvent)
            is MigrationStage.ReadFailed -> ReadFailed(stage, onEvent)
            is MigrationStage.CheckFailed -> CheckFailed(stage, onEvent)
            is MigrationStage.Fallback -> Fallback(stage.import, onEvent, pickFile)
            MigrationStage.Checking, is MigrationStage.Closed -> Unit
        }
    }
}

/** Raden under rubriken för varje läge. */
@StringRes
private fun intro(stage: MigrationStage): Int? = when (stage) {
    MigrationStage.Reading, is MigrationStage.Review -> R.string.migration_intro
    is MigrationStage.Writing -> R.string.migration_writing_intro
    is MigrationStage.Written -> R.string.migration_done_intro
    is MigrationStage.Mismatch, is MigrationStage.WriteFailed -> R.string.migration_not_done_intro
    MigrationStage.Aborting -> R.string.migration_aborting_intro
    is MigrationStage.Stopped -> R.string.migration_stopped_intro
    else -> null
}

// ── Lägena ────────────────────────────────────────────────────────────────

@Composable
private fun Reading() {
    StatusCard(R.drawable.ic_database, stringResource(R.string.migration_reading_title)) {
        AppLoading()
        Muted(stringResource(R.string.migration_reading_note))
    }
}

@Composable
private fun Review(review: MigrationStage.Review, onEvent: (MigrationEvent) -> Unit, onSaveCopy: () -> Unit) {
    CopyCard(review.copy, onSaveCopy)
    Account(review, onEvent)
    StatusCard(
        R.drawable.ic_database,
        stringResource(R.string.migration_found_title),
        count = stringResource(R.string.migration_found_source),
        countTone = Tone.Neutral,
    ) {
        CountRows(review.counts)
    }
    WarningsNote(review.warnings)
    if (review.started) Note(stringResource(R.string.migration_started_note), R.drawable.ic_info, Tone.Neutral)
    RemindersPaused()
    Actions {
        AppButton(stringResource(R.string.migration_move), { onEvent(MigrationEvent.Move) }, Modifier.fillMaxWidth(), enabled = review.canMove)
        if (review.started) Abort(onEvent) else NotNow(onEvent)
    }
}

@Composable
private fun CopyCard(copy: CopyStep, onSave: () -> Unit) {
    val title = stringResource(R.string.migration_copy_title)
    val required = stringResource(R.string.migration_copy_required)
    when (copy) {
        CopyStep.Missing -> StatusCard(R.drawable.ic_file, title, required, Tone.Sun) {
            Muted(stringResource(R.string.migration_copy_missing))
            AppButton(stringResource(R.string.migration_copy_save), onSave, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
        CopyStep.Checking -> StatusCard(R.drawable.ic_file, title) {
            AppLoading()
            Muted(stringResource(R.string.migration_copy_checking))
        }
        is CopyStep.Saved -> StatusCard(R.drawable.ic_file, title, stringResource(R.string.migration_copy_verified), Tone.Positive) {
            copy.fileName?.let { Text(it, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurface) }
            val posts = pluralStringResource(R.plurals.migration_posts, copy.total, countText(copy.total))
            val saved = "${DateFormat.weekdayDay(copy.savedAt.date)} ${DateFormat.time(copy.savedAt.time)}"
            Muted(stringResource(R.string.migration_copy_saved_format, saved, posts))
        }
        is CopyStep.Stale -> StatusCard(R.drawable.ic_warning, stringResource(R.string.migration_copy_stale_title), required, Tone.Sun) {
            Muted(stringResource(R.string.migration_copy_stale, DateFormat.weekdayDay(copy.savedOn)))
            AppButton(stringResource(R.string.migration_copy_save_new), onSave, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
        is CopyStep.Failed -> StatusCard(R.drawable.ic_warning, stringResource(R.string.migration_copy_failed_title), required, Tone.Sun) {
            Muted(stringResource(copy.reason.message()))
            AppButton(stringResource(R.string.migration_copy_retry), onSave, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
    }
}

@StringRes
private fun CopyFailure.message(): Int = when (this) {
    CopyFailure.WRITE_FAILED -> R.string.migration_copy_write_failed
    CopyFailure.UNREADABLE -> R.string.migration_copy_unreadable
    CopyFailure.COUNT_MISMATCH -> R.string.migration_copy_count_mismatch
    CopyFailure.CONVERTER_STOPPED, CopyFailure.DOCUMENTS_DIFFER -> R.string.migration_copy_differs
    CopyFailure.STATE_FAILED -> R.string.migration_copy_state_failed
}

/**
 * Kontot som datan flyttas till (OMB-2): samma som i 3.x visar e-posten, ett annat är en varning, och ett okänt
 * 3.x-konto kräver ett uttryckligt kryss ("Ja, flytta till …") innan flytten går att starta.
 */
@Composable
private fun Account(review: MigrationStage.Review, onEvent: (MigrationEvent) -> Unit) {
    val email = review.accountEmail
    val text = when (review.accountCheck) {
        AccountCheck.SAME -> email?.let { stringResource(R.string.migration_account, it) } ?: stringResource(R.string.migration_account_unknown)
        AccountCheck.DIFFERENT -> email?.let { stringResource(R.string.migration_account_mismatch, it) } ?: stringResource(R.string.migration_account_unknown)
        AccountCheck.UNKNOWN -> email?.let { stringResource(R.string.migration_account_check, it) } ?: stringResource(R.string.migration_account_check_unknown)
    }
    Note(text, R.drawable.ic_person, if (review.accountCheck == AccountCheck.DIFFERENT) Tone.Warning else Tone.Neutral)
    if (review.accountCheck == AccountCheck.UNKNOWN) {
        AppCard {
            CheckRow(
                email?.let { stringResource(R.string.migration_account_confirm, it) } ?: stringResource(R.string.migration_account_confirm_unknown),
                checked = review.accountConfirmed,
                onCheckedChange = { onEvent(MigrationEvent.ConfirmAccount(it)) },
            )
        }
    }
}

@Composable
private fun Writing(writing: MigrationStage.Writing) {
    WritingCard(stringResource(R.string.migration_writing_title), writing.before, writing.done)
    Actions { AppButton(stringResource(R.string.migration_open), {}, Modifier.fillMaxWidth(), enabled = false) }
}

@Composable
private fun Written(written: MigrationStage.Written, onEvent: (MigrationEvent) -> Unit) {
    DoneCard(stringResource(R.string.migration_done_title), written.before, written.after, accounted(written.after, written.existing))
    Note(stringResource(R.string.migration_done_note), R.drawable.ic_check, Tone.Positive)
    val existing = written.existing.values.sum()
    if (existing > 0) Note(pluralStringResource(R.plurals.migration_existing, existing, countText(existing)), R.drawable.ic_info, Tone.Neutral)
    Actions {
        AppButton(
            stringResource(R.string.migration_confirm),
            { onEvent(MigrationEvent.Confirm) },
            Modifier.fillMaxWidth(),
            loading = written.confirming,
        )
        Abort(onEvent)
    }
}

@Composable
private fun Mismatch(mismatch: MigrationStage.Mismatch, onEvent: (MigrationEvent) -> Unit) {
    MismatchCard(mismatch.before, mismatch.accounted, mismatch.mismatched, stringResource(R.string.migration_nothing_confirmed))
    RemindersPaused()
    RetryOrAbort(onEvent)
}

@Composable
private fun WriteFailed(failed: MigrationStage.WriteFailed, onEvent: (MigrationEvent) -> Unit) {
    WriteFailedCard(
        stringResource(R.string.migration_failed_title),
        stringResource(failed.error.toMessage()),
        failed.batch,
        failed.code,
        stringResource(R.string.migration_nothing_confirmed),
    )
    RemindersPaused()
    if (failed.started) RetryOrAbort(onEvent) else RetryOrNotNow(onEvent)
}

@Composable
private fun Aborting() {
    StatusCard(R.drawable.ic_database, stringResource(R.string.migration_aborting_title)) {
        AppLoading()
        Muted(stringResource(R.string.migration_aborting))
    }
}

@Composable
private fun Stopped(stopped: MigrationStage.Stopped, onEvent: (MigrationEvent) -> Unit) {
    StoppedCard(stringResource(R.string.migration_stopped_title), stopped.report)
    Fallback(R.string.migration_untouched_note, onEvent, retry = true)
}

@Composable
private fun WrongVersion(onEvent: (MigrationEvent) -> Unit) {
    ProblemCard(stringResource(R.string.migration_version_title), listOf(stringResource(R.string.migration_version)))
    Fallback(R.string.migration_version_note, onEvent, retry = false)
}

@Composable
private fun ReadFailed(failed: MigrationStage.ReadFailed, onEvent: (MigrationEvent) -> Unit) {
    val reason = failed.error?.let { stringResource(it.toMessage()) } ?: stringResource(R.string.migration_unreadable)
    ProblemCard(stringResource(R.string.migration_unreadable_title), listOf(reason, stringResource(R.string.migration_nothing_written)))
    Fallback(R.string.migration_untouched_note, onEvent, retry = true)
}

@Composable
private fun CheckFailed(failed: MigrationStage.CheckFailed, onEvent: (MigrationEvent) -> Unit) {
    if (failed.error == DataError.Offline) {
        ProblemCard(stringResource(R.string.migration_offline_title), listOf(stringResource(R.string.migration_offline)))
    } else {
        ProblemCard(stringResource(R.string.migration_check_failed_title), listOf(stringResource(failed.error.toMessage())))
    }
    RetryOrNotNow(onEvent)
}

// ── Byggstenar på skärmen (de delade i MigrationParts.kt) ──────────────────

@Composable
private fun RemindersPaused() {
    Note(stringResource(R.string.migration_reminders_paused), R.drawable.ic_bell, Tone.Sun)
}

@Composable
private fun NotNow(onEvent: (MigrationEvent) -> Unit) {
    AppButton(stringResource(R.string.not_now), { onEvent(MigrationEvent.NotNow) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
}

@Composable
private fun RetryOrNotNow(onEvent: (MigrationEvent) -> Unit) {
    Actions {
        AppButton(stringResource(R.string.retry), { onEvent(MigrationEvent.Retry) }, Modifier.fillMaxWidth())
        NotNow(onEvent)
    }
}

@Composable
private fun RetryOrAbort(onEvent: (MigrationEvent) -> Unit) {
    Actions {
        AppButton(stringResource(R.string.retry), { onEvent(MigrationEvent.Retry) }, Modifier.fillMaxWidth())
        Abort(onEvent)
    }
}

/** "Avbryt flytten" (OMB-7): en permanent radering av det flyttade – alltid bakom [ConfirmDialog]. */
@Composable
private fun Abort(onEvent: (MigrationEvent) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AppButton(stringResource(R.string.migration_abort), { confirm = true }, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.migration_abort_title),
            message = stringResource(R.string.migration_abort_message),
            confirmLabel = stringResource(R.string.migration_abort_confirm),
            onConfirm = {
                confirm = false
                onEvent(MigrationEvent.Abort)
            },
            onDismiss = { confirm = false },
            destructive = true,
        )
    }
}

/**
 * Första starten utan Room-fil (OMB-5, mockupen avsnitt 16): valen i ett kort under notisen, sedan importens lägen –
 * samma vy som i Inställningar → Export och import. "Börja tomt" och "Öppna Dagboken" = [MigrationEvent.StartEmpty].
 */
@Composable
private fun Fallback(stage: ImportStage, onEvent: (MigrationEvent) -> Unit, pickFile: () -> Unit) {
    val startEmpty = { onEvent(MigrationEvent.StartEmpty) }
    val import = { event: ImportEvent -> onEvent(MigrationEvent.Import(event)) }
    if (stage == ImportStage.Choose) {
        Note(stringResource(R.string.import_fallback_intro), R.drawable.ic_info, Tone.Neutral)
        AppCard { ImportChoiceRows(onDrive = { import(ImportEvent.FromDrive) }, onFile = pickFile, onStartEmpty = startEmpty) }
    } else {
        ImportStageContent(stage, import, pickFile, stringResource(R.string.migration_open), startEmpty, onStartEmpty = startEmpty)
    }
}

/**
 * Vägarna vidare när 3.x-filen inte kan flyttas (OMB-5, OMB-7): [note] om att filen är orörd, "Försök igen" ([retry]),
 * "Importera backup" (öppnar Export och import, #230) och "Börja tomt" (släpper in i appen som "Inte nu").
 */
@Composable
private fun Fallback(@StringRes note: Int, onEvent: (MigrationEvent) -> Unit, retry: Boolean) {
    Note(stringResource(note), R.drawable.ic_info, Tone.Neutral)
    Actions {
        if (retry) AppButton(stringResource(R.string.retry), { onEvent(MigrationEvent.Retry) }, Modifier.fillMaxWidth())
        AppButton(
            stringResource(if (retry) R.string.migration_import_instead else R.string.migration_import),
            { onEvent(MigrationEvent.ImportBackup) },
            Modifier.fillMaxWidth(),
            variant = if (retry) ButtonVariant.Secondary else ButtonVariant.Primary,
        )
        AppButton(stringResource(R.string.migration_start_empty), { onEvent(MigrationEvent.NotNow) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}

