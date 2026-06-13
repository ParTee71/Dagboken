package se.partee71.dagboken.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Maps to R tokens in src/utils/theme.js:
//   R.sm=6, R.md=14, R.lg=20, R.xl=28, R.pill=999
val DagbokenShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
