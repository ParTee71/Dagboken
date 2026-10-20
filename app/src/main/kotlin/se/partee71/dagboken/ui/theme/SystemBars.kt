package se.partee71.dagboken.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Kant till kant med statusradens och navigeringsfältets ikoner i appens tema ([dark]) – en gång för appens
 * aktiviteter (`MainActivity`, `MomentActivity`).
 */
fun ComponentActivity.applySystemBars(dark: Boolean) {
    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
