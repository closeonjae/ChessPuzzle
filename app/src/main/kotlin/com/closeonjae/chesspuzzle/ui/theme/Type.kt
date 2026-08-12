package com.closeonjae.chesspuzzle.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// DESIGN.md 3절 type scale, verbatim. The puzzle screen is a mostly custom
// layout (DESIGN.md 4절) rather than a list of standard Material slots, so
// these are used directly as `style =` rather than threaded through a
// MaterialTheme.Typography role system we would not otherwise need.
object AppType {
    val turnLabel = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val ratingChip = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val buttonLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
}
