package com.closeonjae.chesspuzzle.core.puzzle

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PuzzleEngineTest {

    /**
     * Fool's mate: 1.f3 e5 2.g4 — mate in one for Black (Qh4#), a single-
     * element solution. gamePgn stops right before the solution move itself
     * (PuzzleEngine replays the whole thing — the class doc explains why
     * initialPly isn't a replay count), leaving Black to move with Qh4# next.
     */
    private fun foolsMate() = PuzzleEngine(
        PuzzleData(
            id = "test-mate-in-1",
            gamePgn = "f3 e5 g4",
            initialPly = 3,
            solution = listOf("d8h4"),
            rating = 900,
        )
    )

    @Test
    fun `replays the whole game and reports the side to move`() {
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
        // Carries what was actually attempted (user request: animate the
        // piece to here, then roll it back) even though the board itself
        // is already reverted by the time this returns.
        assertEquals("b8c6", outcome.attempted.toString())
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

    /**
     * Regression: a real-device crash log showed `board.doMove(String)`
     * *throwing* `MoveConversionException` (not returning false, unlike the
     * coordinate path) for SAN it can't resolve to a legal move — e.g. "Qe2"
     * when no queen can reach e2. Uncaught, this took down the whole app
     * from the keyboard-entry flow. attemptSan now catches it and reports
     * IllegalMove like any other unplayable input.
     */
    @Test
    fun `unparseable SAN input is illegal, not a crash`() {
        val engine = foolsMate()
        val fenBefore = engine.board.fen
        val outcome = engine.attemptSan("Qe2")
        assertIs<MoveOutcome.IllegalMove>(outcome)
        assertEquals(fenBefore, engine.board.fen)
    }

    /**
     * Regression: doMove(Move, fullValidation = true) alone accepted a
     * knight moved straight from c6 to c5 (not even a knight-shaped move,
     * just an empty square) and scored it WrongMove instead of rejecting
     * it — surfaced as the app showing "Try again" for a tap/drag that
     * couldn't possibly have been an attempt at the puzzle's answer.
     * attemptCoordinates now cross-checks board.legalMoves() first.
     */
    @Test
    fun `a geometrically impossible move for the piece is illegal, not wrong`() {
        val engine = PuzzleEngine(
            PuzzleData(
                id = "test-ruy-lopez-illegal",
                gamePgn = "e4 e5 Nf3 Nc6 Bb5",
                initialPly = 5,
                solution = listOf("a7a6", "b5a4", "g8f6"),
                rating = 1200,
            )
        )
        val fenBefore = engine.board.fen
        val outcome = engine.attemptCoordinates(Square.C6, Square.C5)
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

    @Test
    fun `hintMove reports the next expected move without touching the board, and null once solved`() {
        val engine = foolsMate()
        val fenBefore = engine.board.fen
        val hint = engine.hintMove
        assertEquals(Square.D8, hint?.from)
        assertEquals(Square.H4, hint?.to)
        assertEquals(fenBefore, engine.board.fen)

        engine.attemptCoordinates(Square.D8, Square.H4)
        assertEquals(null, engine.hintMove)
    }

    @Test
    fun `lastMove reports the opening replay's final move, then updates as moves are played`() {
        val engine = foolsMate()
        // gamePgn "f3 e5 g4" replayed in full stops right after White's g4
        // — that's the last move on the board before anyone has played
        // anything through the engine itself.
        assertEquals(Square.G2, engine.lastMove?.from)
        assertEquals(Square.G4, engine.lastMove?.to)

        engine.attemptCoordinates(Square.D8, Square.H4)
        assertEquals(Square.D8, engine.lastMove?.from)
        assertEquals(Square.H4, engine.lastMove?.to)
    }

    /**
     * Review walk (user request: step back/forward through the solution once
     * solved). One step for the puzzle's own starting position plus one per
     * solution ply — the opening PGN replay is deliberately not recorded, so
     * the walk is only ever the moves played here.
     */
    @Test
    fun `reviewSteps records the start position plus one step per solution ply`() {
        val engine = PuzzleEngine(
            PuzzleData(
                id = "test-ruy-lopez",
                gamePgn = "e4 e5 Nf3 Nc6 Bb5",
                initialPly = 5,
                solution = listOf("a7a6", "b5a4", "g8f6"),
                rating = 1200,
            )
        )
        // Before anything is played: just the starting position, highlighting
        // the PGN's own last move (Bb5) so a full rewind looks like the start.
        assertEquals(1, engine.reviewSteps.size)
        assertEquals("f1b5", engine.reviewSteps[0].move.toString())

        engine.attemptCoordinates(Square.A7, Square.A6)
        // The solver's move *and* the auto-played opponent reply each get one.
        assertEquals(3, engine.reviewSteps.size)
        assertEquals("a7a6", engine.reviewSteps[1].move.toString())
        assertEquals("b5a4", engine.reviewSteps[2].move.toString())

        engine.attemptSan("Nf6")
        assertEquals(4, engine.reviewSteps.size)
        assertEquals("g8f6", engine.reviewSteps[3].move.toString())
    }

    @Test
    fun `reviewSteps snapshots hold the position as it was at that ply`() {
        val engine = foolsMate()
        val start = engine.reviewSteps[0]
        assertEquals(Piece.BLACK_QUEEN, start.pieces[Square.D8.ordinal])
        assertEquals(Piece.NONE, start.pieces[Square.H4.ordinal])

        engine.attemptCoordinates(Square.D8, Square.H4)
        val solved = engine.reviewSteps.last()
        assertEquals(Piece.NONE, solved.pieces[Square.D8.ordinal])
        assertEquals(Piece.BLACK_QUEEN, solved.pieces[Square.H4.ordinal])
        // The earlier snapshot is a real snapshot, not a live view of the board.
        assertEquals(Piece.BLACK_QUEEN, engine.reviewSteps[0].pieces[Square.D8.ordinal])
    }

    /** A wrong move is undone, so it must leave no trace in the review walk. */
    @Test
    fun `a wrong move does not add a review step`() {
        val engine = foolsMate()
        assertEquals(1, engine.reviewSteps.size)
        assertIs<MoveOutcome.WrongMove>(engine.attemptSan("Nc6"))
        assertEquals(1, engine.reviewSteps.size)
    }

    /**
     * Regression, real data: puzzle "tOfGm", captured verbatim from
     * `PuzzleViewModel`'s `Log.d` on the user's own watch (connected
     * directly via `adb pair`/`connect`) at the exact moment the hint bug
     * was showing "White to move" with the hint on Black's queen.
     * `initialPly=28` is kept verbatim (real API data) even though
     * PuzzleEngine no longer reads it — only `game.pgn`'s own length
     * decides how much gets replayed now (class doc above).
     */
    @Test
    fun `real puzzle tOfGm — hint lands on the solver's own piece, not the opponent's`() {
        val engine = PuzzleEngine(
            PuzzleData(
                id = "tOfGm",
                gamePgn = "d4 e5 dxe5 Nc6 Nc3 Nxe5 Bf4 Nc6 Nf3 d6 Nd4 Bd7 Nxc6 Bxc6 e3 Nf6 Bc4 Be7 " +
                    "O-O Qd7 Nd5 O-O-O Nxf6 gxf6 Bxf7 Rdf8 Bh5 Rfg8 f3",
                initialPly = 28,
                solution = listOf("d7h3", "f1f2", "h3h5"),
                rating = 1500,
            )
        )
        assertEquals(Side.BLACK, engine.sideToMove)
        val hint = engine.hintMove
        assertEquals(Square.D7, hint?.from)
        assertEquals(Square.H3, hint?.to)
        assertEquals(engine.sideToMove, engine.board.getPiece(hint!!.from).pieceSide)
    }

    /**
     * Regression, same real puzzle: the actual crash the previous
     * (reverted-and-refixed) `initialPly - 1` guess still had — playing
     * the correct first move (d7h3) then trying to auto-play the
     * opponent's reply (f1f2) crashed with `IllegalStateException:
     * Solution reply 'f1f2' was illegal`, straight out of a UI tap
     * handler with nothing catching it. With the full-`game.pgn` replay
     * this now completes normally all the way to Solved.
     */
    @Test
    fun `real puzzle tOfGm — the full solution sequence plays through to Solved`() {
        val engine = PuzzleEngine(
            PuzzleData(
                id = "tOfGm",
                gamePgn = "d4 e5 dxe5 Nc6 Nc3 Nxe5 Bf4 Nc6 Nf3 d6 Nd4 Bd7 Nxc6 Bxc6 e3 Nf6 Bc4 Be7 " +
                    "O-O Qd7 Nd5 O-O-O Nxf6 gxf6 Bxf7 Rdf8 Bh5 Rfg8 f3",
                initialPly = 28,
                solution = listOf("d7h3", "f1f2", "h3h5"),
                rating = 1500,
            )
        )
        val first = engine.attemptCoordinates(Square.D7, Square.H3)
        assertIs<MoveOutcome.Correct>(first)
        assertEquals("f1f2", first.opponentReply?.toString())

        val second = engine.attemptCoordinates(Square.H3, Square.H5)
        assertIs<MoveOutcome.Solved>(second)
        assertTrue(engine.isSolved)
    }
}
