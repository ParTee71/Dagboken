package se.partee71.dagboken.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Appens former (skill `ui-style`, DSN-3): kort 22 dp, chips och knappar helt rundade, ark 28 dp upptill. */
object AppShapes {
    private val CARD_RADIUS = 22.dp

    val card: Shape = RoundedCornerShape(CARD_RADIUS)

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

    /**
     * Kärnkänslan som nav överst i känsloväljaren (`FeelingPicker`, SCR-7): en halv skiva – hörnen är hela höjden,
     * så att ytan blir en halvcirkel när bredden är dubbla höjden.
     */
    val feelingHub: Shape = RoundedCornerShape(topStartPercent = 100, topEndPercent = 100, bottomEndPercent = 0, bottomStartPercent = 0)

    /** En nyans i känsloväljaren: ett blad – rundat i två motstående hörn, som i prototypen (#318). */
    val feelingNuance: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 8.dp, bottomEnd = 26.dp, bottomStart = 8.dp)

    /** Ikonrutan i ett tomt tillstånd – rundare än ett kort, för att den är större. */
    val iconTile: Shape = RoundedCornerShape(40.dp)

    /** Formerna som android-ui:s komponenter läser (`AppTheme`) – samma värden som ovan. */
    internal val theme = se.partee71.ui.theme.AppShapes(
        cardRadius = CARD_RADIUS,
        row = row,
        pill = pill,
        field = field,
        menu = menu,
        sheet = sheet,
        smallTile = smallTile,
        iconTile = iconTile
    )
}
