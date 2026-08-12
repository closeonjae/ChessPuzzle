package com.closeonjae.chesspuzzle.ui.theme

import androidx.compose.ui.unit.dp

// DESIGN.md 4절, verbatim.
object Dimens {
    /** Board side = screen diameter / sqrt(2): the largest square inscribed in the round
     * safe area with all four corners inside the circle (no clipping) — DESIGN.md 4절. */
    const val BoardInsetRatio = 0.7071f

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
