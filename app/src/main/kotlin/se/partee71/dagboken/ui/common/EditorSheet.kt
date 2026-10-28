package se.partee71.dagboken.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import se.partee71.dagboken.ui.common.AppErrors
import se.partee71.ui.common.EditorState
import se.partee71.ui.common.UiError
import se.partee71.ui.common.Validator

/** Hur länge klart-läget ("Sparat", NFR-21) visas innan arket döljs. */
const val SAVED_CONFIRMATION_MILLIS = 700L

/**
 * Ett öppet formulär i ett ark: formuläret ([editor], den delade [EditorState]), det sparade värdet [loaded]
 * (`null` = nytt) och [context] – det arket visar utöver formuläret (t.ex. rubriken), taget när arket öppnades,
 * så att arket står sig också när skärmen bakom laddar om. [error] är ett skrivfel med samma text som i
 * `EntityEditScreen` (`EditorUiState.failure`), visat i arket (`AppBottomSheet(error)`). [closing] = sparat:
 * arket visar klart-läget (`StepwiseScreeningNavigation(done)`, formuläret låst med `blockInput`); efter
 * [SAVED_CONFIRMATION_MILLIS] blir [hiding] sant och arket döljs animerat (`AppBottomSheet(hide)`) och stängs sedan
 * ([EditorSheet.close]).
 */
class EditorSheetState<T, C> internal constructor(
    val editor: EditorState<T>,
    val loaded: T?,
    val context: C,
    scope: CoroutineScope
) {
    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error.asStateFlow()

    /** En ny post vars sparning misslyckats – osparad tills den sparats eller slängts, också om felet försvinner. */
    private val failedNew = MutableStateFlow(false)

    /**
     * Om arket ska fråga "Släng ändringar?" innan det stängs (`AppBottomSheet(dirty)`): ändrat, eller en ny post
     * vars sparning misslyckats – det som skulle sparas finns ingen annanstans.
     */
    val unsaved: StateFlow<Boolean> =
        combine(editor.state, failedNew) { state, failed -> state.isDirty || failed }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _closing = MutableStateFlow(false)
    val closing: StateFlow<Boolean> = _closing.asStateFlow()

    /**
     * Om arket får stängas just nu – inte medan det sparas. Läser formulärets aktuella värde direkt (inte det
     * senast komponerade), så att "Spara" och bakåt i samma bildruta aldrig döljer arket (`AppBottomSheet(canDismiss)`).
     */
    fun canDismiss(): Boolean = !editor.state.value.saving

    internal fun showError(error: UiError?) {
        _error.value = error
        if (error != null && loaded == null) failedNew.value = true
    }

    private val _hiding = MutableStateFlow(false)
    val hiding: StateFlow<Boolean> = _hiding.asStateFlow()

    internal fun saved() {
        failedNew.value = false
        _closing.value = true
    }

    internal fun hide() {
        _hiding.value = true
    }
}

/**
 * Livscykeln för ett formulär i ett ark – en gång för alla ark (måendearket på Idag, och efter hand
 * incheckningen och plusknappens formulär). Ihop med `AppBottomSheet(dirty, canDismiss, hide)` och samma
 * [EditorState] som `EntityEditScreen` (NFR-10):
 * - [open] öppnar ett ark; ett nytt objekt ([open] utan sparat värde) får sparas oförändrat (`saveUnchanged`).
 * - [save] markerar formuläret som sparande direkt (synkront), så att arket inte går att stänga eller ändra
 *   under sparningen ([EditorSheetState.canDismiss]); sparningen går fort (Firestore skriver i cachen).
 * - Sparat → [EditorSheetState.closing] (klart-läge; inga fler ändringar eller sparningar), efter
 *   [SAVED_CONFIRMATION_MILLIS] [EditorSheetState.hiding]: skärmen döljer arket animerat, som andra stängningar,
 *   och stänger det sedan med [close]. Fel → [EditorSheetState.error] och arket står kvar; en ändring tar bort felet.
 *
 * [scope] är ViewModelns; [current] är arket som visas, eller `null`.
 */
class EditorSheet<T, C>(private val scope: CoroutineScope) {
    private val _current = MutableStateFlow<EditorSheetState<T, C>?>(null)
    val current: StateFlow<EditorSheetState<T, C>?> = _current.asStateFlow()

    /** Arkets egna coroutines (resultaten, `unsaved`) – avslutas när arket stängs. */
    private var job: Job? = null

    /** Öppnar ett ark med [value] och [context]; [loaded] är det sparade värdet (`null` = nytt). */
    fun open(loaded: T?, value: T, context: C, validator: Validator<T> = Validator { emptyMap() }) {
        job?.cancel()
        val sheetJob = Job(scope.coroutineContext[Job])
        val sheetScope = scope + sheetJob
        val sheet = EditorSheetState(EditorState(value, validator, AppErrors, saveUnchanged = loaded == null), loaded, context, sheetScope)
        job = sheetJob
        _current.value = sheet
        sheetScope.launch {
            // Formulärets händelser kvitteras här, i arkets livscykel – arket visar dem som eget tillstånd.
            sheet.editor.state.collect { state ->
                if (state.done) {
                    sheet.editor.onDoneHandled()
                    sheet.saved()
                    launch {
                        delay(SAVED_CONFIRMATION_MILLIS)
                        sheet.hide()
                    }
                }
                state.failure?.let { failure ->
                    sheet.editor.onFailureShown()
                    sheet.showError(failure)
                }
            }
        }
    }

    /**
     * Ändrar det öppna formuläret med [transform] på det aktuella värdet (inte under en sparning); [fields]
     * markeras som rörda, så att deras fel syns – som i `EntityEditScreen`.
     */
    fun update(vararg fields: String, transform: (T) -> T) {
        val sheet = _current.value ?: return
        if (sheet.editor.state.value.saving || sheet.closing.value) return
        sheet.showError(null)
        sheet.editor.update(*fields, transform = transform)
    }

    /**
     * Sparar med [write] (sparat värde, formulärets värde) när "Spara" är aktiv ([EditorUiState.canSave]). Startar
     * utan dispatch, så att formuläret är markerat som sparande innan anropet returnerar.
     */
    fun save(write: suspend (loaded: T?, value: T) -> Result<Unit>) {
        val sheet = _current.value ?: return
        if (!sheet.editor.state.value.canSave || sheet.closing.value) return
        sheet.showError(null)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { sheet.editor.save { write(sheet.loaded, it) } }
    }

    /** Stänger arket – efter "Släng ändringar?", utan ändringar eller när det sparats och dolts; aldrig under en sparning. */
    fun close() {
        val sheet = _current.value ?: return
        if (!sheet.canDismiss()) return
        _current.value = null
        job?.cancel()
        job = null
    }
}
