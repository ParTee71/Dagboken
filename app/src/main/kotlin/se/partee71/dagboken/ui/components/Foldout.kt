package se.partee71.dagboken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec

/**
 * Appens ihopfällbara sektion (NFR-18) — används både som fristående avsnitt inuti ett
 * kort och som hela innehållet i ett ihopfällbart **sektionskort** (t.ex. Trenders
 * diagramkort, TRD-14). Bygg inte en egen variant; utöka den här (regel 4).
 *
 * **Hela titelraden** växlar utfällt läge, till skillnad från postkortets chevron-knapp
 * (NFR-16). Skälet är att postkortets tryck redan är upptaget av "öppna posten", medan en
 * sektion inte har någon konkurrerande primär åtgärd — samma princip som listradens
 * "tryck på hela raden" (NFR-17). Raden håller därför minst [MIN_TOUCH_TARGET] i höjd och
 * exponerar `Role.Button` samt en `stateDescription` för läget, medan chevronen är en ren
 * indikator som inte läses upp separat.
 *
 * [trailing] renderas i titelraden mellan titeln och chevronen — avsett för en kontroll
 * som hör ihop med det utfällda innehållet (t.ex. Trenders periodväljare). Innehåll som
 * saknar mening när sektionen är stängd ska anroparen utelämna i stängt läge.
 * [titleStyle]/[titleColor] finns för att ett sektionskort ska kunna behålla kortets egen
 * rubriktypografi; standardvärdena är avsnittsrubrikens.
 */
@Composable
fun Foldout(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.labelLarge,
    titleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue  = if (expanded) 180f else 0f,
        animationSpec = DagbokenAnimSpec.springNormal,
        label         = "foldout_chevron",
    )
    // Åtgärdsetiketten delas med postkortets chevron (NFR-16) så samma gest heter
    // samma sak i hela appen; tillståndet läses upp separat via stateDescription.
    val clickLabel = stringResource(if (expanded) R.string.collapse else R.string.expand)
    val stateLabel = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateLabel }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                // Ren tillståndsindikator — raden bär både åtgärden och tillståndet.
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
            exit = shrinkVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
        ) {
            content()
        }
    }
}
