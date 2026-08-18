package com.closeonjae.chesspuzzle.ui.theme

import androidx.compose.ui.graphics.Color

// Token values are DESIGN.md 2절, verbatim — single source of truth is that
// document; this file is the one place they get transcribed into code
// (DEVELOP.md "디자인 값이 코드로 전달되는 경로를 정하라" / 단일 소스).

// App chrome
val Background = Color(0xFF000000)
val Surface = Color(0xFF1C1B1A)
val TextPrimary = Color(0xFFF5F1EA)
val TextSecondary = Color(0xFFA69C8D)
val Accent = Color(0xFFC15F3C)
val Success = Color(0xFF4C9A6A)
val ErrorColor = Color(0xFFD14343)
// Rating delta — Korean market convention (red = up, blue = down), brighter
// than errorColor/accent so the small chip text still reads clearly.
val RatingUp = Color(0xFFFF5C5C)
val RatingDown = Color(0xFF4FA8FF)

// Chessboard — square colors sampled directly from the user-provided LS/DS
// reference PNGs; piece artwork is cut from those same images (see
// PieceIcons.kt) rather than drawn in code, so there are no separate piece
// fill/outline tokens here anymore — DESIGN.md 2절.
val BoardLight = Color(0xFFEDF3FA)
val BoardDark = Color(0xFF5A99F2)
// Selected square: solid RGB average of boardLight/boardDark (user
// request) — a neutral tone distinct from both square colors, rather than
// a translucent accent tint over whichever color the square already was.
val SelectedSquare = Color(
    red = (BoardLight.red + BoardDark.red) / 2f,
    green = (BoardLight.green + BoardDark.green) / 2f,
    blue = (BoardLight.blue + BoardDark.blue) / 2f,
)
val LegalDot = Color(0x40000000) // rgba(0,0,0,0.25) — legal-move dot/capture-ring
val LastMoveOutline = Color(0xE6C15F3C) // rgba(193,95,60,0.9)
// Last-moved from/to squares (user request) — the same translucent wash
// treatment as hintTint below, just repurposed: this used to *be*
// hintTint's yellow before the hint/last-move colors were swapped.
val LastMoveTint = Color(0x60FFFF00) // rgba(255,255,0,0.375)
// Hint square wash (user request) — translucent color sitting between the
// square's own background and the piece drawn on top of it. Color history:
// red 50% -> yellow 50% -> yellow 25% -> yellow 37.5% -> back to red (same
// 37.5% alpha), once last-moveTint above took over yellow for its own use.
val HintTint = Color(0x60FF0000) // rgba(255,0,0,0.375)

// Opening explorer (DESIGN.md 9.2절). Contrast ratios in the comments were
// computed against this file's own values, not eyeballed.
/** 1dp rim around the candidate-move badge. Accent alone is 3.77:1 on light squares but only 1.46:1 on dark ones; this reads 15.3:1 / 5.9:1 against them, so the badge keeps its shape on both. */
val MarkerHalo = Color(0xFF1A1A1A)
/** White-wins share of a win/draw/loss bar — the same value as [BoardLight], reused rather than a second near-white. */
val WdlWhite = Color(0xFFEDF3FA)
/** Draw share. 3.2:1 against [WdlWhite]. */
val WdlDraw = Color(0xFF8A8178)
/** Black-wins share. 3.13:1 against [WdlDraw]; true black would vanish into the OLED background instead of reading as a segment. */
val WdlBlack = Color(0xFF3E3A36)
/** Outline of a standalone bar. [WdlBlack] is only 1.87:1 against the background, so this (3.6:1) is what shows where the bar ends. Unused on the ECO chip's bar, whose extent is the chip itself. */
val WdlFrame = Color(0xFF6D6459)
