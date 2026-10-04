package se.partee71.dagboken.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Appens former (skill `ui-style`, DSN-3): kort 22 dp, chips och knappar helt rundade, ark 28 dp upptill. */
object AppShapes {
    private val CARD_RADIUS = 22.dp

    val card: Shape = RoundedCornerShape(CARD_RADIUS)

    /** En bit av ett kort som delas upp i rader (lat lista): rundad bara upptill och/eller nedtill. */
    fun cardSegment(top: Boolean, bottom: Boolean): Shape = RoundedCornerShape(
        topStart = if (top) CARD_RADIUS else 0.dp,
        topEnd = if (top) CARD_RADIUS else 0.dp,
        bottomStart = if (bottom) CARD_RADIUS else 0.dp,
        bottomEnd = if (bottom) CARD_RADIUS else 0.dp,
    )

    /** Rader och datumremsans chip. */
    val row: Shape = RoundedCornerShape(18.dp)
    val pill: Shape = CircleShape

    /** Textfält – rundare än Material-standard men tydligt ett fält, inte en knapp. */
    val field: Shape = RoundedCornerShape(14.dp)

    /** Menyer. */
    val menu: Shape = RoundedCornerShape(16.dp)

    /** Bottom sheet: rundat upptill. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Liten ikonruta, t.ex. i en sektionsrubrik. */
    val smallTile: Shape = RoundedCornerShape(10.dp)

    /** Ikonrutan i ett tomt tillstånd – rundare än ett kort, för att den är större. */
    val iconTile: Shape = RoundedCornerShape(40.dp)
}
