package com.closeonjae.chesspuzzle.ui.theme

import androidx.compose.ui.unit.dp

// DESIGN.md 4절, verbatim.
object Dimens {
    /** Board side, as a fraction of screen diameter. The pure "inscribed square"
     * value (1/sqrt(2) ≈ 0.7071) touches the circle with nothing else — but the
     * ranks column sits *outside* the board's own left edge, so at that ratio it
     * has nowhere left to go without leaving the round safe area (confirmed on an
     * emulator screenshot: the rank 8/1 digits were partly clipped). Shrunk to
     * leave real margin for the ranks column — DESIGN.md 4절. */
    const val BoardInsetRatio = 0.64f

    val ButtonHeight = 52.dp
    val ButtonCornerRadius = 26.dp

    val ChipCornerRadius = 8.dp
    val ChipPaddingH = 10.dp
    val ChipPaddingV = 3.dp

    val RanksColumnWidth = 13.dp
    val BoardRowGap = 3.dp
    val KeyboardTabWidth = 22.dp
    val KeyboardTabMarginStart = 4.dp
    /** Fraction of the board's side used for the keyboard tab's height. */
    const val KeyboardTabHeightRatio = 0.62f

    val LastMoveOutlineWidth = 2.dp
}
