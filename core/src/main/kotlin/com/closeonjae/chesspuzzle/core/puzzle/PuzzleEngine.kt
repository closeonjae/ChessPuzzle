package com.closeonjae.chesspuzzle.core.puzzle

import com.closeonjae.chesspuzzle.core.lichess.PuzzleAndGame
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveConversionException

/** The minimal puzzle shape [PuzzleEngine] needs, decoupled from the API response type. */
data class PuzzleData(
    val id: String,
    val gamePgn: String,
    val initialPly: Int,
    val solution: List<String>,
    val rating: Int,
)

fun PuzzleAndGame.toPuzzleData(): PuzzleData = PuzzleData(
    id = puzzle.id,
    gamePgn = game.pgn,
    initialPly = puzzle.initialPly,
    solution = puzzle.solution,
    rating = puzzle.rating,
)

/**
 * Result of one attempted move (RESEARCH.md 3/6/8절, PLAN.md 5절).
 * [Correct]/[Solved]/[WrongMove] all carry the actual [Move] played — the
 * UI uses these to animate the piece sliding between squares (user
 * request) instead of it just appearing at its new square.
 */
sealed interface MoveOutcome {
    /** Correct so far; [solverMove] is what was just played, [opponentReply] is the auto-played reply, if the puzzle isn't solved yet. */
    data class Correct(val solverMove: Move, val opponentReply: Move?) : MoveOutcome

    /** The puzzle's final move — no opponent reply follows. [solverMove] is null only for the no-op "already solved" case. */
    data class Solved(val solverMove: Move? = null) : MoveOutcome

    /**
     * A legal chess move, but not the puzzle's solution move. [attempted]
     * is what was tried — the board itself is left unchanged (the move is
     * undone internally before this is returned), but the UI still
     * animates the piece traveling to [attempted]'s destination and then
     * rolling back (user request), using this square pair rather than the
     * (already-reverted) board state.
     */
    data class WrongMove(val attempted: Move) : MoveOutcome

    /** Not a legal move / unparseable input — the board is left unchanged. */
    object IllegalMove : MoveOutcome
}

/**
 * Drives one Lichess puzzle on top of chesslib (bhlangonijr/chesslib —
 * RESEARCH.md 8절). `puzzle.solution` alternates solver / opponent moves
 * starting with the SOLVER at index 0 — the position at the end of
 * `game.pgn` is already the solver's turn, nothing is auto-played on load.
 *
 * The real root cause of the long-running hint-color bug (DESIGN.md 5절
 * "힌트" 항목의 전체 기록, RESEARCH.md 5-A절): `game.pgn` should be replayed
 * **in full** — `initialPly` does not control how many tokens to replay at
 * all. A first fix guessed `initialPly` tokens, a second guessed
 * `initialPly - 1`; both left `solution[0]` legal in isolation but broke
 * on `solution[1]` (the auto-played opponent reply), because neither
 * actually reached the true position. Proven with two independent real
 * puzzles (the official `/next` docs example and one pulled live off the
 * user's own watch via `adb pair`/`connect` + `PuzzleViewModel`'s `Log.d`)
 * replayed through python-chess: only replaying every token of `game.pgn`
 * makes the *entire* solution sequence legal move-by-move; every partial
 * count — including both earlier guesses — breaks on some move in the
 * sequence, not just the first.
 */
class PuzzleEngine(private val puzzle: PuzzleData) {

    val board: Board = Board()
    private var solutionIndex = 0

    val id: String get() = puzzle.id
    val rating: Int get() = puzzle.rating
    val isSolved: Boolean get() = solutionIndex >= puzzle.solution.size

    init {
        // game.pgn is space-separated SAN tokens with no move numbers
        // (RESEARCH.md 3절 example response) — replay every one of them to
        // reach the puzzle's starting position (see class doc above:
        // initialPly is not a replay count).
        val sanMoves = puzzle.gamePgn.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        for ((i, san) in sanMoves.withIndex()) {
            check(board.doMove(san)) { "Failed to replay '$san' at ply $i for puzzle ${puzzle.id}" }
        }
    }

    /**
     * The side the solver is playing — fixed for this puzzle's whole
     * lifetime, captured once the PGN replay above reaches the puzzle's
     * actual starting position. Real-emulator finding: PuzzleScreen's board
     * used to flip orientation off the *live* [sideToMove] instead, which
     * flips the instant the puzzle's final move is played (solving it hands
     * the turn to the opponent) — the board would visibly spin 180° right
     * as "Correct" appeared, with nothing left to flip back for since no
     * more moves follow. Use this instead of [sideToMove] anywhere the
     * board's *orientation* is decided.
     */
    val solverSide: Side = board.sideToMove

    val sideToMove: Side get() = board.sideToMove

    /**
     * The most recently played move on the board, if any — for last-move-
     * square highlighting (DESIGN.md 5절). Reuses chesslib's own move-
     * history stack, so this also reflects the puzzle's opening replay
     * (not just moves made during play).
     */
    val lastMove: Move? get() = board.backup.lastOrNull()?.move

    /** The solver's next expected move, for the hint button (DESIGN.md 5절) — doesn't touch the board. */
    val hintMove: Move? get() = if (isSolved) null else Move(puzzle.solution[solutionIndex], board.sideToMove)

    /**
     * Attempt the solver's move given as SAN (e.g. "Nc3") — the keyboard-
     * entry flow (DESIGN.md 5절).
     *
     * Real-device crash: unlike the coordinate path, `board.doMove(String)`
     * doesn't just return `false` on input it can't make sense of — it
     * *throws* `MoveConversionException` (confirmed from an actual crash
     * log: typing "Qe2" when no queen could legally reach e2 crashed the
     * whole app). Caught here and treated the same as any other
     * unplayable input.
     */
    fun attemptSan(san: String): MoveOutcome = attempt {
        try {
            board.doMove(san)
        } catch (e: MoveConversionException) {
            false
        }
    }

    /** Attempt the solver's move given as a tapped from/to square pair. */
    fun attemptCoordinates(from: Square, to: Square): MoveOutcome {
        if (isSolved) return MoveOutcome.Solved()
        // doMove(Move, fullValidation = true) turned out to *still* not
        // fully validate a raw coordinate pair — confirmed empirically that
        // it happily played e.g. a knight from c6 straight to c5 (not even
        // a knight-shaped move) as long as the mover's own piece sat on
        // `from`, scoring it WrongMove instead of rejecting it outright.
        // board.legalMoves() is chesslib's own move *generator*, so
        // membership there is the actual source of truth for "can this
        // piece really reach this square" — check it before ever touching
        // the board, rather than trusting doMove's own validation.
        val legalMove = board.legalMoves().firstOrNull { it.from == from && it.to == to }
            ?: return MoveOutcome.IllegalMove
        return attempt { board.doMove(legalMove, true) }
    }

    private inline fun attempt(playOnBoard: () -> Boolean): MoveOutcome {
        if (isSolved) return MoveOutcome.Solved()
        val expectedUci = puzzle.solution[solutionIndex]
        if (!playOnBoard()) return MoveOutcome.IllegalMove

        val played = board.backup.last().move
        if (played.toString() != expectedUci) {
            board.undoMove()
            return MoveOutcome.WrongMove(played)
        }
        solutionIndex++
        val opponentReply = playOpponentReplyIfAny()
        return if (isSolved) MoveOutcome.Solved(played) else MoveOutcome.Correct(played, opponentReply)
    }

    /** Auto-plays the opponent's forced reply, if the solution has one left. */
    private fun playOpponentReplyIfAny(): Move? {
        if (isSolved) return null
        val replyUci = puzzle.solution[solutionIndex]
        val reply = Move(replyUci, board.sideToMove)
        check(board.doMove(reply, true)) { "Solution reply '$replyUci' was illegal for puzzle ${puzzle.id}" }
        solutionIndex++
        return reply
    }
}
