package com.closeonjae.chesspuzzle.core.opening

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/**
 * The line being explored: a chesslib board plus the UCI moves that produced
 * it, from the initial position (PLAN.md 9.4절). Free play — both sides are
 * the user's to move, there is no solution to match against.
 *
 * The UCI list is kept alongside the board rather than derived from it because
 * it *is* the explorer's query parameter (`play=`), and the server needs the
 * whole sequence to name the opening, not just the final position — see
 * `explorerUrl`'s doc / RESEARCH.md 11-A절.
 *
 * Built on the same chesslib primitives `PuzzleEngine` uses, and for the same
 * reason: `board.legalMoves()` is chesslib's own move generator, so membership
 * in it is the real source of truth for "can this piece reach that square".
 * `doMove(Move, fullValidation = true)` alone is not — it was caught playing a
 * knight straight down a file in the puzzle engine's own tests.
 */
class OpeningLine {

    private val board = Board()
    private val _uciMoves = mutableListOf<String>()

    /** Comma-separated UCI, the explorer's `play` parameter. Empty at the initial position. */
    val play: String get() = _uciMoves.joinToString(",")

    val sideToMove: Side get() = board.sideToMove

    /** The move that produced the current position, for last-move square highlighting. */
    val lastMove: Move? get() = board.backup.lastOrNull()?.move

    val canUndo: Boolean get() = _uciMoves.isNotEmpty()

    /**
     * An immutable snapshot of the whole board, indexed by `Square.ordinal` —
     * the same shape `PuzzleEngine.ReviewStep` uses, and for the same reason:
     * the UI then renders from a plain value instead of reading a mutable
     * chesslib board mid-recomposition.
     */
    fun pieces(): List<Piece> = List(64) { board.getPiece(Square.squareAt(it)) }

    /** Legal destinations of the piece on [from], each mapped to whether landing there captures. */
    fun legalDestinations(from: Square): Map<Square, Boolean> =
        board.legalMoves()
            .filter { it.from == from }
            .associate { it.to to (board.getPiece(it.to) != Piece.NONE) }

    /** True if [square] holds a piece belonging to whoever is to move — i.e. something the user can pick up. */
    fun isMovablePieceAt(square: Square): Boolean {
        val piece = board.getPiece(square)
        return piece != Piece.NONE && piece.pieceSide == board.sideToMove
    }

    /** Plays the legal move from [from] to [to], or returns null if there isn't one. */
    fun play(from: Square, to: Square): Move? =
        board.legalMoves().firstOrNull { it.from == from && it.to == to }?.let(::doMove)

    /**
     * Plays a move given as UCI — how the explorer identifies its candidate
     * moves, so this is the path a tap on a marker or a list row takes.
     * Matched against the generated legal moves rather than parsed, so a UCI
     * string that isn't legal here is rejected instead of corrupting the board.
     */
    fun playUci(uci: String): Move? =
        board.legalMoves().firstOrNull { it.toString() == uci }?.let(::doMove)

    private fun doMove(move: Move): Move? {
        if (!board.doMove(move, true)) return null
        _uciMoves += move.toString()
        return move
    }

    /** Steps one move back. Returns false at the initial position. */
    fun undo(): Boolean {
        if (_uciMoves.isEmpty()) return false
        board.undoMove()
        _uciMoves.removeAt(_uciMoves.lastIndex)
        return true
    }
}
