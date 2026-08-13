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
