package se.partee71.dagboken.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.scene.Scene

/**
 * Skärmbyten – samma rörelse överallt, definierad bara här (skill `ui-style`): den nya skärmen
 * glider in en bit och tonas fram med fjädring; tillbaka är samma rörelse baklänges.
 */
object Transitions {
    private val slide = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 500f)
    private val fade = spring<Float>(stiffness = 700f)

    val forward: AnimatedContentTransitionScope<Scene<AppKey>>.() -> ContentTransform = {
        (slideInHorizontally(slide) { it / SHIFT } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(slide) { -it / SHIFT } + fadeOut(fade))
    }

    val back: AnimatedContentTransitionScope<Scene<AppKey>>.() -> ContentTransform = {
        (slideInHorizontally(slide) { -it / SHIFT } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(slide) { it / SHIFT } + fadeOut(fade))
    }

    val predictiveBack: AnimatedContentTransitionScope<Scene<AppKey>>.(Int) -> ContentTransform = { back() }

    /** Hur stor del av bredden skärmen glider: en fjärdedel. */
    private const val SHIFT = 4
}
