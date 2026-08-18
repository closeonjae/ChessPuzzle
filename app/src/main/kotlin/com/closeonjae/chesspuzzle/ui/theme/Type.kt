package com.closeonjae.chesspuzzle.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// DESIGN.md 3절 type scale, verbatim. The puzzle screen is a mostly custom
// layout (DESIGN.md 4절) rather than a list of standard Material slots, so
// these are used directly as `style =` rather than threaded through a
// MaterialTheme.Typography role system we would not otherwise need.
object AppType {
    // DESIGN.md originally specified 20sp here; a real-device (emulator)
    // screenshot showed that overflowing the round screen's narrow top arc
    // and colliding with the board — corrected down after seeing the actual
    // render, not just the illustrative HTML mockup's proportions.
    val turnLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val ratingChip = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val buttonLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
}

// DESIGN.md 9절 — opening explorer screens.
object OpeningType {
    // Both lines pin lineHeight rather than inheriting the font's own: the two
    // of them have to fit inside the 70px cap above the board, and an
    // unspecified line height left that up to the platform font's metrics.
    /** Opening family, line 1 of the top label. */
    val openingFamily = TextStyle(fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)
    /** Variation, line 2 of the top label. */
    val openingVariation = TextStyle(fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Normal)
    /**
     * The rank digit inside a candidate-move badge. 10sp is the largest a
     * single digit can be inside a badge that fits a ~21dp square.
     *
     * `letterSpacing = 0` and tabular figures are both about centering: any
     * tracking is added *after* the glyph and lands inside the measured width,
     * and proportional figures give each digit its own side bearings, so
     * either one leaves the digit sitting off-center inside the ring (user
     * report: 숫자가 오른쪽으로 치우쳐 보임 — measured at ~1px on all five badges).
     */
    val markerDigit = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )
    /** SAN of a move in the candidate list. */
    val moveSan = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    /** The opening name a listed move leads to, when it names a new one. */
    val moveOpening = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal)
}
