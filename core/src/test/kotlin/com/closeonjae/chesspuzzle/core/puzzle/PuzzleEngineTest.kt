package com.closeonjae.chesspuzzle.core.puzzle

import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PuzzleEngineTest {

    /** Fool's mate: 1.f3 e5 2.g4 — mate in one for Black (Qh4#), a single-element solution. */
    private fun foolsMate() = PuzzleEngine(
        PuzzleData(
            id = "test-mate-in-1",
            gamePgn = "f3 e5 g4 Qh4",
            initialPly = 3,
            solution = listOf("d8h4"),
            rating = 900,
        )
    )

    @Test
    fun `replays the game up to initialPly and reports the side to move`() {
        val engine = foolsMate()
        assertEquals(Side.BLACK, engine.sideToMove)
        assertFalse(engine.isSolved)
    }

    @Test
    fun `solving the final move via tapped coordinates reports Solved`() {
        val engine = foolsMate()
        val outcome = engine.attemptCoordinates(Square.D8, Square.H4)
        assertIs<MoveOutcome.Solved>(outcome)
        assertTrue(engine.isSolved)
    }

    @Test
    fun `solving the final move via SAN reports Solved`() {
        val engine = foolsMate()
        val outcome = engine.attemptSan("Qh4")
        assertIs<MoveOutcome.Solved>(outcome)
        assertTrue(engine.isSolved)
    }

    @Test
    fun `a legal but wrong move is rejected and the board is left unchanged`() {
        val engine = foolsMate()
        val fenBefore = engine.board.fen
        val outcome = engine.attemptSan("Nc6")
        assertIs<MoveOutcome.WrongMove>(outcome)
        assertFalse(engine.isSolved)
        assertEquals(fenBefore, engine.board.fen)
    }

    @Test
    fun `an illegal move is rejected without touching the board`() {
        val engine = foolsMate()
        val fenBefore = engine.board.fen
        val outcome = engine.attemptCoordinates(Square.A1, Square.A2)
        assertIs<MoveOutcome.IllegalMove>(outcome)
        assertEquals(fenBefore, engine.board.fen)
    }

    /** Ruy Lopez: 1.e4 e5 2.Nf3 Nc6 3.Bb5 a6 4.Ba4 Nf6 — solver plays twice with one auto-replied opponent move. */
    @Test
    fun `a multi-move puzzle auto-plays the opponent reply and only solves on the final move`() {
        val engine = PuzzleEngine(
            PuzzleData(
                id = "test-ruy-lopez",
                gamePgn = "e4 e5 Nf3 Nc6 Bb5",
                initialPly = 5,
                solution = listOf("a7a6", "b5a4", "g8f6"),
                rating = 1200,
            )
        )
        assertEquals(Side.BLACK, engine.sideToMove)

        val first = engine.attemptCoordinates(Square.A7, Square.A6)
        assertIs<MoveOutcome.Correct>(first)
        assertEquals("b5a4", first.opponentReply?.toString())
        assertFalse(engine.isSolved)
        assertEquals(Side.BLACK, engine.sideToMove)

        val second = engine.attemptSan("Nf6")
        assertIs<MoveOutcome.Solved>(second)
        assertTrue(engine.isSolved)
    }

    @Test
    fun `attempting a move after the puzzle is solved is a no-op`() {
        val engine = foolsMate()
        engine.attemptSan("Qh4")
        val fenAfterSolve = engine.board.fen
        val outcome = engine.attemptSan("Kf2")
        assertIs<MoveOutcome.Solved>(outcome)
        assertEquals(fenAfterSolve, engine.board.fen)
    }
}
