package com.closeonjae.chesspuzzle.core.puzzle

import com.closeonjae.chesspuzzle.core.lichess.PuzzleAndGame
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

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
 * starting with the SOLVER at index 0 (verified against lichess-org/lila's
 * ui/puzzle/src/ctrl.ts + moveTest.ts — the training UI does not auto-play
 * an opening move, the position at `initialPly` is already the solver's turn).
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

    /** Attempt the solver's move given as SAN (e.g. "Nc3") — the keyboard-entry flow (DESIGN.md 5절). */
    fun attemptSan(san: String): MoveOutcome = attempt { board.doMove(san) }

    /** Attempt the solver's move given as a tapped from/to square pair. */
    fun attemptCoordinates(from: Square, to: Square): MoveOutcome {
        val uci = from.toString().lowercase() + to.toString().lowercase()
        // The 2-arg doMove(Move) overload turned out not to fully validate
        // (empirically: it happily "moved" an opposing piece out of turn in
        // testing) — doMove(Move, fullValidation = true) is the one that
        // actually rejects an illegal from/to pair.
        return attempt { board.doMove(Move(uci, board.sideToMove), true) }
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
