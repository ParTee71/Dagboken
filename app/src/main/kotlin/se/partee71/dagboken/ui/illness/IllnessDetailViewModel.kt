package se.partee71.dagboken.ui.illness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.EndDateError
import se.partee71.dagboken.core.engine.IllnessSummary
import se.partee71.dagboken.core.engine.endDateError
import se.partee71.dagboken.core.engine.illnessSummary
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.ui.common.DetailLoader
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.days
import se.partee71.dagboken.ui.common.failureOrNull

/**
 * Det sjukdomsdetaljen visar: episoden med sina incheckningar, senaste först ([summary], räknad i `:core`), dagen
 * idag (en pågående episods varaktighet och radera-frågans period) och symptomens namn ur Listor ([symptomNames]).
 * [deleting] = episoden raderas just nu: inga åtgärder (ny incheckning, Redigera, Avsluta, incheckningarnas meny).
 */
data class IllnessDetail(
    val summary: IllnessSummary,
    val today: LocalDate,
    val symptomNames: Map<String, String> = emptyMap(),
    val deleting: Boolean = false,
) {
    /** SJ-4: "Avsluta episod" finns för en pågående episod som inte börjar efter idag ([endDateError] med idag som slut). */
    val canFinish: Boolean get() = summary.ongoing && endDateError(summary.episode.start, today, today) == null
}

/** Frågan som är öppen i detaljen. */
sealed interface IllnessPrompt {
    /** "Avsluta episoden?" med slutdatumet [end] (förval idag); [error] = det valda bryter mot [endDateError] (SJ-4). */
    data class Finish(val end: LocalDate, val error: EndDateError? = null) : IllnessPrompt

    /** "Radera sjukdomsepisoden?" – [checkins] incheckningar raderas med den (SJ-9). */
    data class Delete(val checkins: Int) : IllnessPrompt
}

sealed interface IllnessDetailEvent {
    /** "Avsluta episod" (SJ-4) – öppnar frågan med idag som slutdatum. */
    data object Finish : IllnessDetailEvent

    data class FinishDate(val end: LocalDate) : IllnessDetailEvent

    data object ConfirmFinish : IllnessDetailEvent

    /** Radera i toppradens meny (SJ-9) – räknar incheckningarna och öppnar frågan. */
    data object Delete : IllnessDetailEvent

    data object ConfirmDelete : IllnessDetailEvent

    data object DismissPrompt : IllnessDetailEvent

    /** Bekräftad radering av en incheckning (svep eller menyn på postkortet, HIST-5). */
    data class DeleteCheckin(val id: String) : IllnessDetailEvent

    data object Retry : IllnessDetailEvent

    data object ErrorShown : IllnessDetailEvent
}

/**
 * Sjukdomsdetaljen (SJ-4, SJ-5, SJ-9, SJ-12, SJ-13, HIST-9, HEM-12) för episoden [id]: läsning via [DetailLoader]
 * (laddning, fel, "Försök igen"; en raderad episod är "finns inte"), avsluta med slutdatum, radera episoden med sina
 * incheckningar (kaskad, kräver nät) och radera en incheckning. Fel från en åtgärd visas som meddelande ([failure]);
 * efter en lyckad radering är [closed] `true` och detaljen stängs.
 */
@HiltViewModel(assistedFactory = IllnessDetailViewModel.Factory::class)
class IllnessDetailViewModel @AssistedInject constructor(
    private val illnesses: IllnessRepository,
    options: OptionsRepository,
    clock: Clock,
    zone: Provider<TimeZone>,
    @Assisted private val id: String,
) : ViewModel() {
    /** Symptomens namn – ett läsfel ger bara svårighetsgraden i undertexten (`withFallback`). */
    private val names: Flow<Map<String, String>> = options.observe(OptionKind.SYMPTOM).withFallback(emptyList())
        .map { all -> all.associate { it.id to it.name } }
        .distinctUntilChanged()

    private val loader = DetailLoader(
        combine(illnesses.observeEpisode(id), clock.days { zone.get() }, names) { loaded, today, names ->
            loaded?.let { IllnessDetail(illnessSummary(it.episode, it.checkins, today), today, names) }
        },
        viewModelScope,
    )

    /** En radering pågår: det som visas står kvar tills den är klar, i stället för incheckningar som försvinner och "finns inte". */
    private val deleting = MutableStateFlow(false)

    /**
     * Det senast laddade innehållet – frågorna (avsluta, radera) gäller det, också när skärmen nyss prenumererat om och
     * läget tillfälligt är laddning.
     */
    private var lastContent: IllnessDetail? = null

    val state: StateFlow<DetailUiState<IllnessDetail>> = combine(loader.state, deleting, ::Pair)
        .scan<Pair<DetailUiState<IllnessDetail>, Boolean>, DetailUiState<IllnessDetail>>(DetailUiState.Loading) { shown, (next, frozen) ->
            when {
                frozen && shown is DetailUiState.Content -> DetailUiState.Content(shown.value.copy(deleting = true))
                frozen && next is DetailUiState.Content -> DetailUiState.Content(next.value.copy(deleting = true))
                else -> next
            }
        }
        .onEach { state -> (state as? DetailUiState.Content)?.let { lastContent = it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0), DetailUiState.Loading)

    private val _prompt = MutableStateFlow<IllnessPrompt?>(null)
    val prompt: StateFlow<IllnessPrompt?> = _prompt.asStateFlow()

    private val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure.asStateFlow()

    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    fun onEvent(event: IllnessDetailEvent) {
        when (event) {
            IllnessDetailEvent.Finish -> lastContent?.takeIf { it.canFinish && !deleting.value }?.let { _prompt.value = IllnessPrompt.Finish(it.today) }
            is IllnessDetailEvent.FinishDate -> (_prompt.value as? IllnessPrompt.Finish)?.let { _prompt.value = IllnessPrompt.Finish(event.end) }
            IllnessDetailEvent.ConfirmFinish -> finish()
            IllnessDetailEvent.Delete -> askDelete()
            IllnessDetailEvent.ConfirmDelete -> delete()
            IllnessDetailEvent.DismissPrompt -> _prompt.value = null
            is IllnessDetailEvent.DeleteCheckin -> if (!deleting.value) report { illnesses.deleteCheckin(id, event.id) }
            IllnessDetailEvent.Retry -> loader.retry()
            IllnessDetailEvent.ErrorShown -> _failure.value = null
        }
    }

    /**
     * SJ-4: ett slutdatum som [endDateError] avvisar (före starten, efter idag) stänger inte frågan – fältet visar felet
     * och ingenting skrivs. Gäller det senast laddade innehållet; finns inget (läsfel sedan frågan öppnades) stängs
     * frågan med felet.
     */
    private fun finish() {
        val asked = _prompt.value as? IllnessPrompt.Finish ?: return
        val detail = lastContent ?: return closeWith(DataError.NotFound)
        val episode = detail.summary.episode
        endDateError(episode.start, asked.end, detail.today)?.let { error ->
            _prompt.value = asked.copy(error = error)
            return
        }
        _prompt.value = null
        report { illnesses.finishEpisode(episode, asked.end, detail.today) }
    }

    /** SJ-9: frågan nämner antalet incheckningar på servern; går det inte att räkna (offline), de som visas. */
    private fun askDelete() {
        val detail = lastContent ?: return
        if (deleting.value) return
        viewModelScope.launch {
            val count = illnesses.checkinCount(id).getOrElse { detail.summary.checkins.size }
            _prompt.value = IllnessPrompt.Delete(count)
        }
    }

    private fun closeWith(error: DataError) {
        _prompt.value = null
        _failure.value = Failure(error)
    }

    /** SJ-9: kaskaden kräver nät – offline raderas ingenting och felet visas; lyckad stänger detaljen. */
    private fun delete() {
        if (_prompt.value !is IllnessPrompt.Delete || deleting.value) return
        _prompt.value = null
        deleting.value = true
        viewModelScope.launch {
            val result = illnesses.deleteEpisode(id)
            if (result.isSuccess) {
                _closed.value = true
            } else {
                deleting.value = false
                _failure.value = result.failureOrNull()
            }
        }
    }

    private fun report(action: suspend () -> Result<Unit>) {
        viewModelScope.launch { action().failureOrNull()?.let { _failure.value = it } }
    }

    @AssistedFactory
    interface Factory {
        fun create(id: String): IllnessDetailViewModel
    }
}
