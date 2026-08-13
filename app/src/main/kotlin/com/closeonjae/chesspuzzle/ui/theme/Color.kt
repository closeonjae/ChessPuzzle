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
// Hint square wash (user request) — translucent yellow (originally red at
// 50% alpha, changed on user feedback) sitting between the square's own
// background and the piece drawn on top of it. Alpha: 50% -> 25% -> 37.5%,
// each a separate user request.
val HintTint = Color(0x60FFFF00) // rgba(255,255,0,0.375)
