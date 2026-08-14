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

/** Result of one attempted move (RESEARCH.md 3/6/8절, PLAN.md 5절). */
sealed interface MoveOutcome {
    /** Correct so far; [opponentReply] is the auto-played reply, if the puzzle isn't solved yet. */
    data class Correct(val opponentReply: Move?) : MoveOutcome

    /** The puzzle's final move — no opponent reply follows. */
    object Solved : MoveOutcome

    /** A legal chess move, but not the puzzle's solution move. The board is left unchanged (move is undone). */
    object WrongMove : MoveOutcome

    /** Not a legal move / unparseable input — the board is left unchanged. */
    object IllegalMove : MoveOutcome
}

/**
 * Drives one Lichess puzzle on top of chesslib (bhlangonijr/chesslib —
 * RESEARCH.md 8절). `puzzle.solution` alternates solver / opponent moves
 * starting with the SOLVER at index 0 — the position at `initialPly` is
 * already the solver's turn, nothing is auto-played on load.
 *
 * (This exact assumption was flipped once already and had to be reverted —
 * DESIGN.md 5절 "힌트" 항목의 버그 수정 기록 참고. A real-device screenshot showed
 * the hint button pointing at the opponent's own piece, which looked
 * exactly like Lichess's puzzle *database* convention where solution[0] is
 * an opponent setup move (database.lichess.org/#puzzles). Auto-playing
 * solution[0] to match that "fixed" the hint but broke real puzzle loading
 * almost entirely — most fetches started failing right after construction,
 * surfacing as constant "Connection Lost". Whatever the real explanation
 * is (this REST API's game.pgn+initialPly+solution shape apparently
 * doesn't follow the CSV database export's convention, or something else
 * was off), it's reverted here pending an actual raw API response to
 * confirm against, rather than guessing a second time and risking another
 * regression.)
 */
class PuzzleEngine(private val puzzle: PuzzleData) {

    val board: Board = Board()
    private var solutionIndex = 0

    val id: String get() = puzzle.id
    val rating: Int get() = puzzle.rating
    val isSolved: Boolean get() = solutionIndex >= puzzle.solution.size

    init {
        // game.pgn is space-separated SAN tokens with no move numbers
        // (RESEARCH.md 3절 example response) — replay up to initialPly to
        // reach the puzzle's starting position.
        val sanMoves = puzzle.gamePgn.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        for (i in 0 until puzzle.initialPly.coerceAtMost(sanMoves.size)) {
            check(board.doMove(sanMoves[i])) { "Failed to replay '${sanMoves[i]}' at ply $i for puzzle ${puzzle.id}" }
        }
    }

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
        if (isSolved) return MoveOutcome.Solved
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
        if (isSolved) return MoveOutcome.Solved
        val expectedUci = puzzle.solution[solutionIndex]
        if (!playOnBoard()) return MoveOutcome.IllegalMove

        val played = board.backup.last().move
        if (played.toString() != expectedUci) {
            board.undoMove()
            return MoveOutcome.WrongMove
        }
        solutionIndex++
        val opponentReply = playOpponentReplyIfAny()
        return if (isSolved) MoveOutcome.Solved else MoveOutcome.Correct(opponentReply)
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
