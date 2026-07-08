package se.partee71.dagboken.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object DagbokenAnimSpec {
    val springNormal: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMedium,
    )
    val springBouncy: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness    = Spring.StiffnessMediumLow,
    )
    val tweenFast: TweenSpec<Float>   = tween(durationMillis = 150)
    val tweenNormal: TweenSpec<Float> = tween(durationMillis = 300)

    /** Samma fjäderparametrar som [springNormal], typad för andra värden än Float
     * (t.ex. `IntSize` i `expandVertically`/`shrinkVertically`, `IntOffset` i `animateItem`). */
    fun <T> springNormalSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMedium,
    )

    /** Samma varaktighet som [tweenNormal], typad för andra värden än Float. */
    fun <T> tweenNormalSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 300)
}
