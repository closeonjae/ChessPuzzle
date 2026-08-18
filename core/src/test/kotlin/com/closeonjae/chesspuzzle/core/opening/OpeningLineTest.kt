package com.closeonjae.chesspuzzle.core.opening

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpeningLineTest {

    /** 1.e4 e5 2.Nf3 Nc6 3.Bb5 — the Ruy Lopez, the line the design mockup uses. */
    private fun ruyLopez() = OpeningLine().apply {
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5").forEach { assertNotNull(playUci(it), it) }
    }

    @Test
    fun `a new line starts at the initial position with an empty play sequence`() {
        val line = OpeningLine()
        assertEquals("", line.play)
        assertEquals(Side.WHITE, line.sideToMove)
        assertFalse(line.canUndo)
        assertNull(line.lastMove)
        assertEquals(Piece.WHITE_KING, line.pieces()[Square.E1.ordinal])
        assertEquals(Piece.NONE, line.pieces()[Square.E4.ordinal])
    }

    @Test
    fun `playing coordinates accumulates the explorer's play parameter`() {
        val line = OpeningLine()
        assertNotNull(line.play(Square.E2, Square.E4))
        assertNotNull(line.play(Square.C7, Square.C5))

        assertEquals("e2e4,c7c5", line.play)
        assertEquals(Side.WHITE, line.sideToMove)
        assertEquals(Piece.WHITE_PAWN, line.pieces()[Square.E4.ordinal])
        assertEquals(Piece.NONE, line.pieces()[Square.E2.ordinal])
    }

    @Test
    fun `the ruy lopez replays through the uci path`() {
        val line = ruyLopez()
        assertEquals("e2e4,e7e5,g1f3,b8c6,f1b5", line.play)
        assertEquals(Side.BLACK, line.sideToMove)
        assertEquals(Piece.WHITE_BISHOP, line.pieces()[Square.B5.ordinal])
        assertEquals(Piece.BLACK_KNIGHT, line.pieces()[Square.C6.ordinal])
        assertEquals(Square.F1, line.lastMove?.from)
        assertEquals(Square.B5, line.lastMove?.to)
    }

    @Test
    fun `an illegal coordinate pair is rejected and leaves the position untouched`() {
        val line = OpeningLine()
        // A knight cannot travel straight down a file — the exact class of
        // move that doMove(fullValidation = true) was caught allowing in
        // PuzzleEngine, which is why this goes through legalMoves() instead.
        assertNull(line.play(Square.B1, Square.B3))
        assertEquals("", line.play)
        assertEquals(Piece.WHITE_KNIGHT, line.pieces()[Square.B1.ordinal])
    }

    @Test
    fun `moving an opponent piece is rejected`() {
        val line = OpeningLine()
        assertNull(line.play(Square.E7, Square.E5))
        assertEquals("", line.play)
    }

    @Test
    fun `a uci string that is not legal here is rejected rather than parsed onto the board`() {
        val line = OpeningLine()
        assertNull(line.playUci("e2e5"))
        assertNull(line.playUci("nonsense"))
        assertEquals("", line.play)
    }

    @Test
    fun `undo walks the line back move by move`() {
        val line = ruyLopez()

        assertTrue(line.undo())
        assertEquals("e2e4,e7e5,g1f3,b8c6", line.play)
        assertEquals(Side.WHITE, line.sideToMove)
        assertEquals(Piece.WHITE_BISHOP, line.pieces()[Square.F1.ordinal])
        assertEquals(Piece.NONE, line.pieces()[Square.B5.ordinal])

        repeat(4) { assertTrue(line.undo()) }
        assertEquals("", line.play)
        assertFalse(line.canUndo)
        assertFalse(line.undo())
        assertEquals(Piece.WHITE_PAWN, line.pieces()[Square.E2.ordinal])
    }

    @Test
    fun `undo then replay reaches the same position and play sequence`() {
        val line = ruyLopez()
        val before = line.pieces()

        assertTrue(line.undo())
        assertNotNull(line.playUci("f1b5"))

        assertEquals("e2e4,e7e5,g1f3,b8c6,f1b5", line.play)
        assertEquals(before, line.pieces())
    }

    @Test
    fun `legal destinations mark which of them capture`() {
        // 1.e4 d5 — the black pawn on d5 can be taken by exd5.
        val line = OpeningLine()
        assertNotNull(line.play(Square.E2, Square.E4))
        assertNotNull(line.play(Square.D7, Square.D5))

        val destinations = line.legalDestinations(Square.E4)
        assertEquals(setOf(Square.E5, Square.D5), destinations.keys)
        assertEquals(false, destinations[Square.E5])
        assertEquals(true, destinations[Square.D5])
    }

    @Test
    fun `only the side to move can be picked up`() {
        val line = OpeningLine()
        assertTrue(line.isMovablePieceAt(Square.E2))
        assertFalse(line.isMovablePieceAt(Square.E7))
        assertFalse(line.isMovablePieceAt(Square.E4))

        assertNotNull(line.play(Square.E2, Square.E4))
        assertFalse(line.isMovablePieceAt(Square.D2))
        assertTrue(line.isMovablePieceAt(Square.E7))
    }

    @Test
    fun `castling is recorded as the king's own two-square move`() {
        // The explorer identifies castling by the king's UCI, so this has to
        // round-trip as e1g1 rather than a rook move or a special token.
        val line = OpeningLine()
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6").forEach { assertNotNull(line.playUci(it), it) }
        assertNotNull(line.play(Square.E1, Square.G1))

        assertTrue(line.play.endsWith("e1g1"), line.play)
        assertEquals(Piece.WHITE_KING, line.pieces()[Square.G1.ordinal])
        assertEquals(Piece.WHITE_ROOK, line.pieces()[Square.F1.ordinal])
    }
}
