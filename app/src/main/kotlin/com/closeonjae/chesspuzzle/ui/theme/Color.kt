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

// Chessboard — cool blue board + rounded icon-style pieces, matched to a
// reference image the user shared (own artwork inspired by it, not a trace —
// see DESIGN.md 2절 licensing note).
val BoardLight = Color(0xFFDCE7F0)
val BoardDark = Color(0xFF7FA6C6)
val PieceWhiteFill = Color(0xFFFFFFFF)
val PieceOutline = Color(0xFF22364A)
val PieceBlackFill = Color(0xFF1E2A36)
val SelectedSquare = Color(0x73C15F3C) // rgba(193,95,60,0.45)
val LegalDot = Color(0x8CF5F1EA) // rgba(245,241,234,0.55)
val LastMoveOutline = Color(0xE6C15F3C) // rgba(193,95,60,0.9)
