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
    /** The candidate badge's ring. The badge is ring-and-digit only, so this is the whole of it. */
    val MarkerBorderWidth = 1.dp

    // Candidate arrows. All fractions of a square, so they scale with the board.
    /** Arrow line thickness. */
    const val ArrowStrokeRatio = 0.07f
    /** How far the arrow starts from its origin square's centre — clear of the piece standing there. */
    const val ArrowTailInsetRatio = 0.34f
    /** Gap between the arrow tip and the badge it points at. */
    const val ArrowHeadGapRatio = 0.06f
    /** Arrowhead length. */
    const val ArrowHeadLengthRatio = 0.17f
    /** Arrowhead width. */
    const val ArrowHeadWidthRatio = 0.22f
    /** Win/draw/loss bar thickness. Thicker than the 3dp draft now that it is a long bar on its own rather than a chip's edge. */
    val WdlBarHeight = 4.dp
    /** Gap between the board's bottom edge and the bar (user request: 체스판에 가깝게). */
    val WdlBarGap = 3.dp
    /**
     * Bar length, as a fraction of the board's side. Not 1.0: the bar sits
     * *below* the board, where the circle has already narrowed. On a 480px
     * screen the bar's lower edge is at y≈420, whose chord is 318px against the
     * board's 339px — so a board-width bar would have its ends clipped off.
     */
    const val WdlBarWidthRatio = 0.93f
    /**
     * Top of the opening-name block. The cap above the board is only 70px tall
     * on a 480px screen (the board starts at y=70.3), and two lines at 12sp
     * line height need 48px — so the draft's 13dp (26px) pushed the second line
     * 8px *under* the board, where it was hidden (user report). 9dp leaves the
     * block ending at y=66.
     */
    val NameTop = 9.dp
    /** Candidate-list row width — fraction of screen width. */
    const val MoveListWidth = 0.68f
    /** Where the candidate list's first row starts. Its top two corners are the tightest point on this screen. */
    val MoveListTop = 38.dp
    /** Scrim over the board while the candidate list is open — near-opaque, so the list reads rather than the board behind it. */
    const val SheetScrimAlpha = 0.92f
}
