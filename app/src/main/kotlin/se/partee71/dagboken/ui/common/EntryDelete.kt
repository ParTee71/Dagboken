package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DeleteAction

/**
 * HIST-5: posten i raderingens bekräftelse – typen ([format], t.ex. "Aktiviteten %1$s, %2$s"), namnet ([title])
 * och när ("Aktiviteten Promenad, 5 okt kl. 09:00"). En gång för Dagbok och postens formulär.
 */
@Composable
fun entrySubject(@StringRes format: Int, title: String, date: LocalDate, time: LocalTime?): String {
    val day = DateFormat.short(date)
    val whenText = if (time == null) day else stringResource(R.string.diary_when_format, day, DateFormat.time(time))
    return stringResource(format, title, whenText)
}

/**
 * Radering av en post i dess formulär (AKT-9, HAN-1) med samma bekräftelse som i Dagbok (HIST-5): "Radera posten?"
 * och "Aktiviteten Promenad, 5 okt kl. 09:00 raderas med sin anteckning. Det går inte att ångra." – utan dag bara namnet.
 */
@Composable
fun entryDeleteAction(@StringRes format: Int, title: String, date: LocalDate?, time: LocalTime?, onConfirm: () -> Unit): DeleteAction {
    val subject = date?.let { entrySubject(format, title, it, time) } ?: title
    return DeleteAction(stringResource(R.string.diary_delete_title), stringResource(R.string.diary_delete_message, subject), onConfirm)
}
