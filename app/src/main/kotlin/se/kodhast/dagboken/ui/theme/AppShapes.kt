package se.kodhast.dagboken.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * De domänegna formerna i känsloväljaren (SCR-7, skill `ui-style`, DSN-3). Alla andra former – kort 22 dp, rader,
 * chips, fält, menyer, ark – är android-ui:s (`AppTheme.shapes`), samma i alla appar.
 */
object AppShapes {
    /**
     * Kärnkänslan som nav överst i känsloväljaren (`FeelingPicker`, SCR-7): en halv skiva – hörnen är hela höjden,
     * så att ytan blir en halvcirkel när bredden är dubbla höjden.
     */
    val feelingHub: Shape = RoundedCornerShape(topStartPercent = 100, topEndPercent = 100, bottomEndPercent = 0, bottomStartPercent = 0)

    /** En nyans i känsloväljaren: ett blad – rundat i två motstående hörn, som i prototypen (#318). */
    val feelingNuance: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 8.dp, bottomEnd = 26.dp, bottomStart = 8.dp)
}
