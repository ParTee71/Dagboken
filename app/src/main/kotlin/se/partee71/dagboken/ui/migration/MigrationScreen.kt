package se.partee71.dagboken.ui.migration

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import se.partee71.dagboken.ui.components.AppDivider
import se.partee71.dagboken.ui.components.AppLoading
import se.partee71.dagboken.ui.components.ButtonVariant
import se.partee71.dagboken.ui.components.CheckRow
import se.partee71.dagboken.ui.components.ConfirmDialog
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.ProgressBar
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.IconSize
import se.partee71.dagboken.ui.theme.Spacing
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
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        uri?.let { onEvent(MigrationEvent.CopyChosen(it)) }
    }
    EntityDetailScreen(
        state = DetailUiState.Content(state.stage),
        header = null,
        onBack = null,
        modifier = modifier,
        title = stringResource(R.string.migration_title),
        subtitle = intro(state.stage)?.let { stringResource(it) },
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
    if (review.warnings.isNotEmpty()) {
        Note(
            pluralStringResource(R.plurals.migration_warnings, review.warnings.size, review.warnings.size),
            R.drawable.ic_warning,
            Tone.Sun,
            detail = limited(review.warnings).joinToString("\n"),
        )
    }
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
    StatusCard(R.drawable.ic_database, stringResource(R.string.migration_writing_title)) {
        val done = writing.done
        if (done == null) {
            Muted(stringResource(R.string.migration_reading_server))
        } else {
            // Hela flytten, inte en entitet i taget; belöningsläget hör till Idag.
            ProgressBar(done.values.sum(), writing.before.values.sum(), celebrate = false)
        }
        CountRows(writing.before, done ?: writing.before.mapValues { 0 })
    }
    Actions { AppButton(stringResource(R.string.migration_open), {}, Modifier.fillMaxWidth(), enabled = false) }
}

@Composable
private fun Written(written: MigrationStage.Written, onEvent: (MigrationEvent) -> Unit) {
    StatusCard(
        R.drawable.ic_database,
        stringResource(R.string.migration_done_title),
        count = stringResource(R.string.migration_done_verified),
        countTone = Tone.Positive,
    ) {
        CountRows(written.before, written.after, accounted(written.after, written.existing))
    }
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
    val lines = MigrationEntity.entries.filter { entity -> entity.collections.any { it in mismatch.mismatched } }.map { entity ->
        stringResource(
            R.string.migration_mismatch_line,
            stringResource(entity.label),
            countText(entity.count(mismatch.accounted)),
            countText(entity.count(mismatch.before)),
        )
    }
    ProblemCard(
        stringResource(R.string.migration_mismatch_title),
        listOf(stringResource(R.string.migration_mismatch_report)) + lines + stringResource(R.string.migration_nothing_confirmed),
    )
    RemindersPaused()
    RetryOrAbort(onEvent)
}

@Composable
private fun WriteFailed(failed: MigrationStage.WriteFailed, onEvent: (MigrationEvent) -> Unit) {
    val code = failed.batch?.let { stringResource(R.string.migration_failed_code, failed.code, it + 1) }
        ?: stringResource(R.string.migration_failed_code_only, failed.code)
    ProblemCard(
        stringResource(R.string.migration_failed_title),
        listOf(stringResource(failed.error.toMessage()), code, stringResource(R.string.migration_nothing_confirmed)),
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
    val lines = stopped.report.map { line ->
        val entity = MigrationEntity.of(line.collection)?.let { stringResource(it.label) } ?: line.collection
        stringResource(R.string.migration_report_line, entity, line.count, line.field, line.reason)
    }
    ProblemCard(
        stringResource(R.string.migration_stopped_title),
        listOf(stringResource(R.string.migration_report)) + limited(lines) + stringResource(R.string.migration_nothing_written),
    )
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

// ── Byggstenar på skärmen ─────────────────────────────────────────────────

/** Ett kort med ikonruta och rubrik, valfri statuspill och innehåll. */
@Composable
private fun StatusCard(
    @DrawableRes icon: Int,
    title: String,
    count: String? = null,
    countTone: Tone = Tone.Primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard {
        SectionHeader(title, icon = icon, count = count, countTone = countTone)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), content = content)
    }
}

/** Ett läge som inte gick: varningsikon, rubrik och rapportens rader (antal, aldrig innehåll). */
@Composable
private fun ProblemCard(title: String, lines: List<String>) {
    StatusCard(R.drawable.ic_warning, title) {
        lines.forEach { Text(it, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurface) }
    }
}

/**
 * Antal per entitet: bara före ([after] = `null`), eller före → efter med en bock när allt är avklarat och "…" för
 * det som inte flyttats än. Avklarat ([accounted], standard [after]) är efter + behållna + raderade i 4.0 + fanns redan – före =
 * avklarat ger bocken (OMB-7). Varje rad läses som en enhet.
 */
@Composable
private fun CountRows(before: Map<String, Int>, after: Map<String, Int>? = null, accounted: Map<String, Int>? = after) {
    MigrationEntity.entries.forEachIndexed { index, entity ->
        if (index > 0) AppDivider()
        CountRow(stringResource(entity.label), entity.count(before), after?.let(entity::count), accounted?.let(entity::count))
    }
}

@Composable
private fun CountRow(label: String, before: Int, after: Int?, accounted: Int?) {
    val from = countText(before)
    val to = after?.let(::countText)
    val done = accounted != null && accounted == before
    val waiting = accounted == 0 && before > 0
    val text = when {
        to == null -> from
        waiting -> stringResource(R.string.migration_count_pending, from)
        else -> stringResource(R.string.migration_count_change, from, to)
    }
    val description = when {
        to == null -> stringResource(R.string.migration_row_before, label, from)
        done -> stringResource(R.string.migration_row_done, label, from, to)
        waiting -> stringResource(R.string.migration_row_pending, label, from)
        else -> stringResource(R.string.migration_row_after, label, from, to)
    }
    ItemRow(
        label,
        Modifier.clearAndSetSemantics { contentDescription = description },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(text, style = AppTypography.itemTitle, color = MaterialTheme.colorScheme.onSurface)
                if (done) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(IconSize.marker), tint = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Ett meddelande utan åtgärd (konto, varningar, påminnelser, klart). */
@Composable
private fun Note(text: String, @DrawableRes icon: Int, tone: Tone, detail: String? = null) {
    NoticeBanner(text, icon, onClick = null, tone = tone, detail = detail)
}

@Composable
private fun RemindersPaused() {
    Note(stringResource(R.string.migration_reminders_paused), R.drawable.ic_bell, Tone.Sun)
}

@Composable
private fun Actions(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s), content = content)
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
 * Vägarna vidare när 3.x-filen inte kan flyttas (OMB-5, OMB-7): [note] om att filen är orörd, "Försök igen" ([retry]),
 * "Importera backup" och "Börja tomt" – som, tills fallbacken finns (#230), släpper in i appen som "Inte nu".
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

/** Högst [MAX_LINES] rader, och "… och N till" för resten. */
@Composable
private fun limited(lines: List<String>): List<String> {
    val shown = lines.take(MAX_LINES)
    val more = lines.size - shown.size
    return if (more > 0) shown + stringResource(R.string.migration_more, more) else shown
}

private const val MAX_LINES = 5
private const val JSON = "application/json"
