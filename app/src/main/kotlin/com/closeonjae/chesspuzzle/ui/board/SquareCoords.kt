package com.closeonjae.chesspuzzle.ui.board

import com.github.bhlangonijr.chesslib.Square

/**
 * row 0 = rank 8 (top of an unflipped board), col 0 = file A. Integer math
 * off chesslib's enum layout (A1 = ordinal 0 … H8 = 63, rank = ordinal/8,
 * file = ordinal%8) instead of building and parsing the square's name
 * string — this and [squareAt] run per gesture event and per square in the
 * boards' 8×8 loops.
 *
 * Shared by both boards (puzzle and opening) — moved here out of
 * PuzzleScreen when the opening screen needed the same convention, so the two
 * can never drift into disagreeing about which corner row 0 is.
 */
fun rowColOf(square: Square): Pair<Int, Int> =
    (7 - square.rank.ordinal) to square.file.ordinal

/** Inverse of [rowColOf] — see its doc for the row/col convention and why this is index math, not string building. */
fun squareAt(row: Int, col: Int): Square = Square.squareAt((7 - row) * 8 + col)

/** A square's own color, independent of which way the board is being viewed (a1 is always dark). */
fun isLightSquare(row: Int, col: Int): Boolean = (row + col) % 2 == 0
