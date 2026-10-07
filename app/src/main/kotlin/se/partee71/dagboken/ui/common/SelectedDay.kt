package se.partee71.dagboken.ui.common

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.time.shownDate
import se.partee71.dagboken.data.common.UserScope

/**
 * Dagen som visas i Idag (HEM-14) – en gång för appen: Idag sätter den ([select]) och plusknappens formulär loggar
 * mot den (NAV-10, [logDay]). `null` = idag, som följer med över midnatt; en tidigare dag står kvar tills en annan
 * väljs. Bara i minnet, och bara för användaren som valde den ([users]): efter utloggning eller kontobyte är det
 * idag igen, och en ny Idag (ny inloggning, ny aktivitet i samma process) börjar på idag ([reset]).
 */
@Singleton
class SelectedDay @Inject constructor(private val users: UserScope) {
    /** Den valda dagen och användaren som valde den. */
    private val chosen = MutableStateFlow<Pair<String?, LocalDate>?>(null)

    /** Den valda dagen för den inloggade, eller `null` för idag. */
    val date: Flow<LocalDate?> = combine(chosen, users.uid) { chosen, uid -> current(chosen, uid) }.distinctUntilChanged()

    /** Den visade dagen mot [today] (`shownDate`, HEM-14): vald dag, annars idag – aldrig efter idag. */
    fun shown(today: Flow<LocalDate>): Flow<LocalDate> = combine(date, today, ::shownDate).distinctUntilChanged()

    /** Idag väljer [date] (`null` = idag). */
    fun select(date: LocalDate?) {
        chosen.value = date?.let { users.uid.value to it }
    }

    /** Tillbaka till idag – när Idag startar på nytt. */
    fun reset() = select(null)

    /**
     * Dagen som plusknappen loggar mot (NAV-10): den visade dagen när fliken Idag är vald ([onToday]), annars idag
     * (`null` – formuläret tar dagens datum ur klockan när det öppnas).
     */
    fun logDay(onToday: Boolean): LocalDate? = if (onToday) current(chosen.value, users.uid.value) else null

    /** Den valda dagen om den inloggade valde den; har användaren bytts (utloggning, annat konto) glöms den. */
    private fun current(chosen: Pair<String?, LocalDate>?, uid: String?): LocalDate? {
        if (chosen == null) return null
        if (chosen.first == uid) return chosen.second
        this.chosen.compareAndSet(chosen, null)
        return null
    }
}
