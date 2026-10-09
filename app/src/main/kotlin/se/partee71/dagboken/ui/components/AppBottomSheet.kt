@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Bottom sheet med rubrik, t.ex. loggmenyn eller inställningsarket. Öppnas helt direkt. Ett skrivfel
 * ([error], med sin text – som standard `DataError.toMessage()`, samma som `EntityEditScreen`) visas sist i
 * panelen, eftersom panelen ligger över skärmens meddelanden.
 *
 * Ett formulär i arket ([dirty] = osparade ändringar, NFR-10): bakåt, svep ner och tryck utanför stänger inte
 * arket utan frågar "Släng ändringar?" ([DiscardChangesDialog], samma som `EntityEditScreen`) – arket står kvar
 * tills svaret, och "Släng" döljer arket (animerat, som en vanlig stängning) och anropar sedan [onDismiss].
 * [canDismiss] frågas i samma stund som arket ska stängas och ska läsa det aktuella läget (t.ex. ett
 * `StateFlow.value`), inte det senast komponerade – `false` håller arket öppet utan fråga (t.ex. medan
 * formuläret sparas). [hide] = true döljer arket animerat på samma sätt och anropar sedan [onDismiss] – t.ex. när
 * formuläret sparats. Standard: stängs direkt, som tidigare.
 *
 * [footer] = knappraden (t.ex. Spara eller stegnavigeringen, NFR-21): innehållet ovanför scrollar och raden står
 * fast i arkets botten, ovanför navigeringsfältet och tangentbordet, hur högt innehållet än blir. Felet visas då
 * direkt ovanför raden. Standard (`null`): ingen knapprad, innehållet som tidigare.
 */
@Composable
fun AppBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    error: Failure? = null,
    dirty: Boolean = false,
    canDismiss: () -> Boolean = { true },
    hide: Boolean = false,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Inte sparad över ett konfigurationsbyte: efter en rotation är frågan borta och arket står kvar.
    var askDiscard by remember { mutableStateOf(false) }
    // "Släng" är svaret: döljandet som följer ska inte fråga igen.
    val discarding = remember { booleanArrayOf(false) }
    val isDirty by rememberUpdatedState(dirty)
    val allowed by rememberUpdatedState(canDismiss)
    val dismiss by rememberUpdatedState(onDismiss)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) { target ->
        when {
            target != SheetValue.Hidden || discarding[0] -> true
            !allowed() -> false
            isDirty -> {
                askDiscard = true
                false
            }
            else -> true
        }
    }
    val scope = rememberCoroutineScope()
    // Dölj animerat utan att fråga, och stäng sedan: efter "Släng" och när [hide] sätts.
    val hideThenDismiss = {
        discarding[0] = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() }
        Unit
    }
    LaunchedEffect(hide) { if (hide) hideThenDismiss() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = AppShapes.sheet,
        containerColor = AppColors.extended.card,
    ) {
        if (footer == null) {
            Column(
                Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(title, style = AppTypography.sectionTitle, modifier = Modifier.semantics { heading() })
                content()
                error?.let { FieldError(stringResource(it.message)) }
            }
        } else {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Text(title, style = AppTypography.sectionTitle, modifier = Modifier.semantics { heading() })
                    content()
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    error?.let { FieldError(stringResource(it.message)) }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        content = footer,
                    )
                }
            }
        }
        // I arkets innehåll, så att dialogens fönster alltid hamnar ovanpå arkets.
        if (askDiscard) {
            DiscardChangesDialog(
                onConfirm = {
                    askDiscard = false
                    hideThenDismiss()
                },
                onDismiss = { askDiscard = false },
            )
        }
    }
}
