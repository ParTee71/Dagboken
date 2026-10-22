package se.partee71.dagboken.ui.today

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.AsNeededChip
import se.partee71.dagboken.core.engine.PrnAvailability
import se.partee71.dagboken.core.engine.doseFor
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.SectionFoldout
import se.partee71.dagboken.ui.components.favoriteMenuItem
import se.partee71.dagboken.ui.theme.Neutral
import se.partee71.dagboken.ui.theme.Warning
import se.partee71.ui.components.AppCard
import se.partee71.ui.components.AppFilterChip
import se.partee71.ui.components.AppMenuItem
import se.partee71.ui.components.AppMenuPopup
import se.partee71.ui.components.ChipPending
import se.partee71.ui.components.ChipRow
import se.partee71.ui.components.ConfirmDialog
import se.partee71.ui.components.DeleteAction
import se.partee71.ui.components.DeleteConfirmDialog
import se.partee71.ui.components.inactive
import se.partee71.ui.theme.Tone

/** Vad ett snabbval visar när det inte går att ta nu (FAV-13): ikonen, texten efter etiketten, TalkBack-texten och undertexten i "Fler". */
private class PrnMark(@param:DrawableRes val icon: Int, val chipText: String, val supporting: String, @param:StringRes val description: Int, val args: List<Any>)

/**
 * FAV-13: markeringen för [availability] – klockikonen med "från 15:40" (kylperioden, "från i morgon 01:10" efter midnatt)
 * eller spärrikonen med "2 av 2 idag" (dagsgränsen) – eller `null` när medicinen kan tas. Tiden visas i [zone], mot [today].
 */
@Composable
private fun prnMark(availability: PrnAvailability?, today: LocalDate, zone: TimeZone): PrnMark? = when (availability) {
    null, PrnAvailability.Ok -> null

    is PrnAvailability.Cooldown -> {
        val next = availability.nextAllowed.toLocalDateTime(zone)
        val time = DateFormat.time(next.time)
        val from = stringResource(if (next.date > today) R.string.today_prn_from_tomorrow else R.string.today_prn_from, time)
        val early = stringResource(R.string.today_prn_early_supporting, from)
        PrnMark(R.drawable.ic_clock, from, early, R.string.today_prn_early_description, listOf(from))
    }

    is PrnAvailability.DailyLimit -> {
        val count = stringResource(R.string.today_prn_limit_count, availability.taken, availability.limit)
        PrnMark(
            R.drawable.ic_block,
            count,
            stringResource(R.string.today_prn_limit_supporting, availability.taken, availability.limit),
            R.string.today_prn_limit_description,
            listOf(availability.taken, availability.limit)
        )
    }
}

/**
 * Vid behov (FAV-2, FAV-11, FAV-12, FAV-13): favoriterna – vid behov-medicinerna och de stjärnmärkta recepten – som
 * chips efter namn. Tryck loggar (en dos respektive en extrados); långtryck ger för en medicin Redigera, Favorit, Logga i
 * efterhand, Visa anteckning och Radera (HEM-11, FAV-3, FAV-8, FAV-9, FAV-10) och för ett recept Redigera, Ta bort som
 * favorit och Visa anteckning. "Fler" har övriga vid behov-mediciner och receptens under "Recept". Det som inte går att
 * ta nu är markerat i chipet och i "Fler" (FAV-13). Utan vid behov-mediciner och recept visas inget kort.
 */
@Composable
internal fun AsNeededCard(content: TodayContent, onEvent: (TodayEvent) -> Unit, links: TodayLinks, expanded: Boolean, onToggle: () -> Unit) {
    val choices = content.choices
    if (choices.chips.isEmpty() && choices.moreCount == 0) return
    // Nyckeln innehåller typen: ett id är unikt bara inom sin samling.
    var menuFor by remember { mutableStateOf<String?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PrnMedicine?>(null) }
    var noteOf by remember { mutableStateOf<Pair<String, String>?>(null) }
    val canLog = content.isToday
    val todayOnly = stringResource(R.string.today_prn_today_only)
    // En tidigare dag: chipen är tonade och trycket gör ingenting, men de är aktiva så att långtrycksmenyn
    // nås också med TalkBack (ett inaktivt chip får ingen långtrycksåtgärd).
    val pastDay = if (canLog) Modifier else Modifier.inactive(true).semantics { stateDescription = todayOnly }
    AppCard {
        SectionFoldout(stringResource(R.string.medicines_as_needed), expanded, onToggle) {
            if (!canLog) {
                NoticeBanner(
                    stringResource(R.string.today_prn_past_day),
                    R.drawable.ic_info,
                    { onEvent(TodayEvent.SelectDate(content.today)) },
                    tone = Tone.Neutral,
                    onClickLabel = stringResource(R.string.today_show_today)
                )
            }
            ChipRow {
                choices.chips.forEach { chip ->
                    Box {
                        PrnChip(chip, content, onEvent, pastDay) { menuFor = chip.key }
                        val items = chipMenu(
                            chip,
                            onEvent,
                            links,
                            content.date,
                            onNote = { noteOf = chip.displayName to chip.note.orEmpty() },
                            onDelete = { deleting = it }
                        )
                        AppMenuPopup(items, expanded = menuFor == chip.key, onDismiss = { menuFor = null })
                    }
                }
                if (choices.moreCount > 0) {
                    Box {
                        AppFilterChip(
                            stringResource(R.string.today_more_format, choices.moreCount),
                            selected = false,
                            onClick = { if (canLog) moreOpen = true },
                            modifier = pastDay,
                            icon = R.drawable.ic_expand_more
                        )
                        val recipes = stringResource(R.string.today_more_prescriptions)
                        val items = choices.others.map { moreItem(it, content, onEvent) } +
                            choices.prescriptions.map { AppMenuItem(it.title(it.doseFor(content.today)), { onEvent(TodayEvent.LogExtra(it)) }, section = recipes) }
                        AppMenuPopup(items, expanded = moreOpen, onDismiss = { moreOpen = false })
                    }
                }
            }
        }
    }
    deleting?.let { medicine ->
        val action = DeleteAction(stringResource(R.string.delete_named_title, medicine.displayName), stringResource(R.string.prn_delete_message)) {
            onEvent(TodayEvent.DeleteMedicine(medicine))
        }
        DeleteConfirmDialog(action, onDismiss = { deleting = null })
    }
    noteOf?.let { (title, note) ->
        val close = { noteOf = null }
        ConfirmDialog(title, note, stringResource(R.string.close), onConfirm = close, onDismiss = close, dismissLabel = null)
    }
}

/**
 * Ett snabbval som chip: tryck loggar en dos (vid behov-medicin, markerad om kylperioden pågår eller dagsgränsen är nådd,
 * FAV-13) eller en extrados (stjärnmärkt recept, FAV-11 – ingen kylperiod eller dagsgräns).
 */
@Composable
private fun PrnChip(chip: AsNeededChip, content: TodayContent, onEvent: (TodayEvent) -> Unit, pastDay: Modifier = Modifier, onMenu: () -> Unit) {
    val title: String
    val log: TodayEvent
    when (chip) {
        is AsNeededChip.Medicine -> {
            title = chip.medicine.title()
            log = TodayEvent.LogAsNeeded(chip.medicine)
        }

        is AsNeededChip.Recipe -> {
            title = chip.prescription.title(chip.prescription.doseFor(content.today))
            log = TodayEvent.LogExtra(chip.prescription)
        }
    }
    val mark = if (chip is AsNeededChip.Medicine) prnMark(content.unavailable[chip.id], content.today, content.zone) else null
    AppFilterChip(
        title,
        selected = false,
        onClick = { if (content.isToday) onEvent(log) },
        modifier = pastDay,
        icon = if (chip.note.isNullOrBlank()) null else R.drawable.ic_note,
        onLongClick = onMenu,
        onLongClickLabel = stringResource(R.string.more_options),
        pending = mark?.let { ChipPending(it.icon, it.chipText, stringResource(it.description, title, *it.args.toTypedArray())) }
    )
}

/** Raden i "Fler": markerad med samma ikon, dämpad titel och en undertext i varningston när den inte går att ta nu (FAV-13). */
@Composable
private fun moreItem(medicine: PrnMedicine, content: TodayContent, onEvent: (TodayEvent) -> Unit): AppMenuItem {
    val mark = prnMark(content.unavailable[medicine.id], content.today, content.zone)
    return AppMenuItem(
        medicine.title(),
        { onEvent(TodayEvent.LogAsNeeded(medicine)) },
        icon = mark?.icon,
        supporting = mark?.supporting,
        supportingTone = mark?.let { Tone.Warning },
        muted = mark != null
    )
}

/**
 * Långtrycksmenyn på ett snabbval. Medicin (NFR-16, HEM-11): Redigera, Favorit, Logga i efterhand, Visa anteckning, Radera sist.
 * Recept (FAV-12): Redigera, Ta bort som favorit och Visa anteckning – ingen Radera eller Logga i efterhand.
 */
@Composable
private fun chipMenu(
    chip: AsNeededChip,
    onEvent: (TodayEvent) -> Unit,
    links: TodayLinks,
    date: LocalDate,
    onNote: () -> Unit,
    onDelete: (PrnMedicine) -> Unit
): List<AppMenuItem> {
    val edit = AppMenuItem(stringResource(R.string.edit), { if (chip is AsNeededChip.Medicine) links.onEditPrn(chip.id) else links.onEditPrescription(chip.id) }, R.drawable.ic_edit)
    val note = if (chip.note.isNullOrBlank()) null else AppMenuItem(stringResource(R.string.note_show), onNote, R.drawable.ic_note)
    return when (chip) {
        is AsNeededChip.Medicine -> listOfNotNull(
            edit,
            favoriteMenuItem(chip.displayName, chip.medicine.favorite) { onEvent(TodayEvent.ToggleFavorite(chip.medicine)) },
            AppMenuItem(stringResource(R.string.dose_log_later), { links.onLogLater(chip.id, date) }, R.drawable.ic_clock),
            note,
            AppMenuItem(stringResource(R.string.delete), { onDelete(chip.medicine) }, R.drawable.ic_delete, destructive = true)
        )

        is AsNeededChip.Recipe -> listOfNotNull(
            edit,
            favoriteMenuItem(chip.displayName, true) { onEvent(TodayEvent.ToggleRecipeFavorite(chip.prescription)) },
            note
        )
    }
}
