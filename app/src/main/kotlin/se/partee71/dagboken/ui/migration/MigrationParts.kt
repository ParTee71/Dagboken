package se.partee71.dagboken.ui.migration

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppDivider
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.ProgressBar
import se.partee71.dagboken.ui.components.SectionHeader
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.IconSize
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/*
 * Byggstenarna som flytten (MigrationScreen) och importen (ImportContent) delar – en gång (regel 4): kort med
 * ikonruta, rader med antal per entitet, rapportens rader och knappraden. Bara antal, aldrig innehåll.
 */

/** Ett kort med ikonruta och rubrik, valfri statuspill och innehåll. */
@Composable
internal fun StatusCard(
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
internal fun ProblemCard(title: String, lines: List<String>) {
    StatusCard(R.drawable.ic_warning, title) {
        lines.forEach { Text(it, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurface) }
    }
}

/** Skrivningen pågår (Mig-Skriver): först "Läser läget på servern …" ([done] = `null`), sedan framsteget per rad. */
@Composable
internal fun WritingCard(title: String, before: Map<String, Int>, done: Map<String, Int>?) {
    StatusCard(R.drawable.ic_database, title) {
        if (done == null) {
            Muted(stringResource(R.string.migration_reading_server))
        } else {
            // Hela skrivningen, inte en entitet i taget; belöningsläget hör till Idag.
            ProgressBar(done.values.sum(), before.values.sum(), celebrate = false)
        }
        CountRows(before, done ?: before.mapValues { 0 })
    }
}

/** Allt skrivet och kontrollerat mot servern (Mig-Klar): före → efter med bocken per rad. */
@Composable
internal fun DoneCard(title: String, before: Map<String, Int>, after: Map<String, Int>, accounted: Map<String, Int> = after) {
    StatusCard(
        R.drawable.ic_database,
        title,
        count = stringResource(R.string.migration_done_verified),
        countTone = Tone.Positive,
    ) {
        CountRows(before, after, accounted)
    }
}

/** Kontrollen mot servern stämde inte: rapporten med de entiteter som avviker, [footer] sist. */
@Composable
internal fun MismatchCard(before: Map<String, Int>, accounted: Map<String, Int>, mismatched: Map<String, Int>, footer: String?) {
    val lines = MigrationEntity.entries.filter { entity -> entity.collections.any { it in mismatched } }.map { entity ->
        stringResource(
            R.string.migration_mismatch_line,
            stringResource(entity.label),
            countText(entity.count(accounted)),
            countText(entity.count(before)),
        )
    }
    ProblemCard(stringResource(R.string.migration_mismatch_title), listOf(stringResource(R.string.migration_mismatch_report)) + lines + listOfNotNull(footer))
}

/** Skrivningen stannade: felets text, felkoden (med batchnumret när det finns) och [footer] sist. */
@Composable
internal fun WriteFailedCard(title: String, message: String, batch: Int?, code: String, footer: String?) {
    val codeLine = batch?.let { stringResource(R.string.migration_failed_code, code, it + 1) }
        ?: stringResource(R.string.migration_failed_code_only, code)
    ProblemCard(title, listOf(message, codeLine) + listOfNotNull(footer))
}

/**
 * Konverterarens stopp (OMB-3): rapporten (entitet, antal, fält, skäl – aldrig innehåll) och "Inget har skrivits". **Alla**
 * rader visas – en per typ av fynd ([reportOf]), inget "… och N till" som döljer en typ.
 */
@Composable
internal fun StoppedCard(title: String, report: List<ReportLine>) {
    val lines = report.map { line ->
        val entity = MigrationEntity.of(line.collection)?.let { stringResource(it.label) } ?: line.collection
        stringResource(R.string.migration_report_line, entity, line.count, line.field, line.reason)
    }
    ProblemCard(title, listOf(stringResource(R.string.migration_report)) + lines + stringResource(R.string.migration_nothing_written))
}

/** Varningarna i en granskning (t.ex. anteckningar utan sin post): antal och de första raderna. */
@Composable
internal fun WarningsNote(warnings: List<String>) {
    if (warnings.isEmpty()) return
    Note(
        pluralStringResource(R.plurals.migration_warnings, warnings.size, warnings.size),
        R.drawable.ic_warning,
        Tone.Sun,
        detail = limited(warnings).joinToString("\n"),
    )
}

/**
 * Antal per entitet: bara före ([after] = `null`), eller före → efter med en bock när allt är avklarat och "…" för
 * det som inte flyttats än. Avklarat ([accounted], standard [after]) är efter + behållna + raderade i 4.0 + fanns redan – före =
 * avklarat ger bocken (OMB-7). Varje rad läses som en enhet.
 */
@Composable
internal fun CountRows(before: Map<String, Int>, after: Map<String, Int>? = null, accounted: Map<String, Int>? = after) {
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
internal fun Muted(text: String) {
    Text(text, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Ett meddelande utan åtgärd (konto, varningar, påminnelser, klart). */
@Composable
internal fun Note(text: String, @DrawableRes icon: Int, tone: Tone, detail: String? = null) {
    NoticeBanner(text, icon, onClick = null, tone = tone, detail = detail)
}

@Composable
internal fun Actions(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s), content = content)
}

/** Högst [MAX_LINES] rader, och "… och N till" för resten. */
@Composable
internal fun limited(lines: List<String>): List<String> {
    val shown = lines.take(MAX_LINES)
    val more = lines.size - shown.size
    return if (more > 0) shown + stringResource(R.string.migration_more, more) else shown
}

/** Filtypen i dokumentväljaren (kopian, exporten och importen). */
internal const val JSON_MIME = "application/json"

private const val MAX_LINES = 5
