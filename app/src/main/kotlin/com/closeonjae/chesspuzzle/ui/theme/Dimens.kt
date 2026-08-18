package com.closeonjae.chesspuzzle.ui.theme

import androidx.compose.ui.unit.dp

// DESIGN.md 4절, verbatim.
object Dimens {
    /** Board side, as a fraction of screen diameter. The pure "inscribed square"
     * value (1/sqrt(2) ≈ 0.7071) touches the circle with nothing else — the
     * hint tab sits *outside* the board's own left edge, so at this ratio
     * it has nowhere left to go without leaving the round safe area (confirmed
     * on an emulator screenshot: the side tabs are partly clipped).
     * Chosen anyway, per explicit instruction, to maximize the board itself —
     * the clipped side tabs are a known, accepted tradeoff. */
    const val BoardInsetRatio = 0.707f

    val ButtonHeight = 52.dp
    val ButtonCornerRadius = 26.dp

    val ChipCornerRadius = 8.dp
    val ChipPaddingH = 10.dp
    val ChipPaddingV = 3.dp

    val BoardRowGap = 3.dp
    val KeyboardTabWidth = 17.dp
    val KeyboardTabMarginStart = 4.dp
    /** Fraction of the board's side used for the keyboard tab's height. */
    const val KeyboardTabHeightRatio = 0.52f

    val LastMoveOutlineWidth = 2.dp
}

// DESIGN.md 9.3절 — opening explorer screens. The width/position fractions are
// derived from the round screen's chord at the y they sit at, not chosen by
// eye: on a 480px screen the chord is 217px at y=26 and 303px at y=54, which is
// where 0.42/0.58 come from. The puzzle screen's 0.78 clips the first line.
object OpeningDimens {
    /** Opening name, line 1 (family) — fraction of screen width. */
    const val NameWidthLine1 = 0.42f
    /** Opening name, line 2 (variation) — sits lower, where the chord is wider. */
    const val NameWidthLine2 = 0.58f
    /** Candidate badge diameter as a fraction of a square. */
    const val CandidateMarkerRatio = 0.66f
    /** How many candidate moves get a badge on the board. The rest are list-only — a ~21dp square can't carry more than a handful legibly. */
    const val CandidateMarkerLimit = 5
    /** Rim around a candidate badge. */
    val MarkerHaloWidth = 1.dp
    /** Win/draw/loss bar thickness. Its width follows whatever it is attached to. */
    val WdlBarHeight = 3.dp
    /** Candidate-list row width — fraction of screen width. */
    const val MoveListWidth = 0.68f
    /** Where the candidate list's first row starts. Its top two corners are the tightest point on this screen. */
    val MoveListTop = 38.dp
    /** Scrim over the board while the candidate list is open — near-opaque, so the list reads rather than the board behind it. */
    const val SheetScrimAlpha = 0.92f
}
