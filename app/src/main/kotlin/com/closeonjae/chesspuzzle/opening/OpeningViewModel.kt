package com.closeonjae.chesspuzzle.opening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closeonjae.chesspuzzle.core.lichess.ExplorerMove
import com.closeonjae.chesspuzzle.core.lichess.ExplorerOpening
import com.closeonjae.chesspuzzle.core.lichess.ExplorerResponse
import com.closeonjae.chesspuzzle.core.opening.OpeningLine
import com.closeonjae.chesspuzzle.data.OpeningRepository
import com.closeonjae.chesspuzzle.ui.theme.OpeningDimens
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the opening screen draws. The position is a plain immutable
 * snapshot rather than the [OpeningLine] itself — the line's chesslib board is
 * mutable, so a state holding it would be the same object before and after a
 * move and `StateFlow` would drop the emission. `PuzzleEngine.ReviewStep`
 * solves the same problem the same way.
 */
data class OpeningUiState(
    /** Board occupation indexed by `Square.ordinal`. */
    val pieces: List<Piece> = emptyList(),
    val lastMoveFrom: Square? = null,
    val lastMoveTo: Square? = null,
    val selectedSquare: Square? = null,
    /** Legal destinations of [selectedSquare]'s piece, each mapped to whether landing there captures. */
    val legalDestinations: Map<Square, Boolean> = emptyMap(),
    val canUndo: Boolean = false,
    /** The top few candidate moves, for the board's badges and arrows. Empty while a piece is selected. */
    val candidates: List<CandidateMove> = emptyList(),
    val opening: ExplorerOpening? = null,
    val white: Int = 0,
    val draws: Int = 0,
    val black: Int = 0,
    val moves: List<ExplorerMove> = emptyList(),
    /** First lookup of this line — nothing to show yet, so the labels read as placeholders rather than dimmed. */
    val isLoading: Boolean = true,
    /** A lookup is in flight but the previous position's numbers are still on screen — they get dimmed rather than blanked (DESIGN.md 9.5절). */
    val isStale: Boolean = false,
    val isError: Boolean = false,
    val isSheetOpen: Boolean = false,
) {
    val total: Long get() = white.toLong() + draws + black
}

/**
 * One candidate move as the board draws it: an arrow from [from] to [to] with a
 * numbered badge at [to]. [from] is what makes the move readable — the badge
 * alone says a piece lands on f3, not *which* piece goes there.
 */
data class CandidateMove(val rank: Int, val from: Square, val to: Square)

/**
 * Drives free exploration of an opening line: play a move (by tapping the
 * board, a badge, or a list row), see what the position is called and what
 * gets played from it, step back (PLAN.md 9.3절).
 */
class OpeningViewModel(private val repository: OpeningRepository) : ViewModel() {

    private val line = OpeningLine()

    private val _uiState = MutableStateFlow(OpeningUiState())
    val uiState: StateFlow<OpeningUiState> = _uiState.asStateFlow()

    /**
     * The in-flight lookup. Cancelled before starting a new one: Lichess asks
     * for one request at a time (RESEARCH.md 11-C절), and a stale answer
     * landing after a newer move would otherwise repaint the wrong position's
     * statistics.
     */
    private var lookupJob: Job? = null

    init {
        publishPosition()
        lookUp()
    }

    /** A board tap: pick up a piece, put it down, or play a badge's move outright. */
    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        val selected = state.selectedSquare

        if (selected == null) {
            // A badge marks either an empty square or an opponent piece, never
            // one of ours — so playing it here can never shadow picking a piece up.
            val candidateUci = state.moves.firstOrNull { it.destination() == square }?.uci
            when {
                state.candidates.any { it.to == square } && candidateUci != null -> playUci(candidateUci)
                line.isMovablePieceAt(square) -> select(square)
            }
            return
        }

        when {
            square == selected -> select(null)
            line.play(selected, square) != null -> afterMove()
            // Tapping another of our own pieces switches the selection rather
            // than clearing it — the same forgiving behavior as the puzzle board.
            line.isMovablePieceAt(square) -> select(square)
            else -> select(null)
        }
    }

    /** A tap on a move in the candidate list. */
    fun onMovePicked(move: ExplorerMove) {
        _uiState.update { it.copy(isSheetOpen = false) }
        playUci(move.uci)
    }

    fun onUndoTapped() {
        if (line.undo()) afterMove()
    }

    fun onListTapped() {
        _uiState.update { it.copy(isSheetOpen = true, selectedSquare = null, legalDestinations = emptyMap()) }
    }

    fun onSheetDismissed() {
        _uiState.update { it.copy(isSheetOpen = false) }
    }

    /** The right tab turns into this while a lookup has failed (DESIGN.md 9.5절). */
    fun onRetryTapped() = lookUp()

    private fun playUci(uci: String) {
        if (line.playUci(uci) != null) afterMove()
    }

    private fun select(square: Square?) {
        _uiState.update {
            it.copy(
                selectedSquare = square,
                legalDestinations = square?.let(line::legalDestinations) ?: emptyMap(),
            )
        }
    }

    /** The board moved: repaint it immediately, then go find out what the new position is. */
    private fun afterMove() {
        publishPosition()
        lookUp()
    }

    private fun publishPosition() {
        val lastMove = line.lastMove
        _uiState.update {
            it.copy(
                pieces = line.pieces(),
                lastMoveFrom = lastMove?.from,
                lastMoveTo = lastMove?.to,
                selectedSquare = null,
                legalDestinations = emptyMap(),
                canUndo = line.canUndo,
            )
        }
    }

    private fun lookUp() {
        lookupJob?.cancel()
        val play = line.play

        // A position already looked up (every position reached by stepping
        // back, and any re-walked line) resolves with no request at all and no
        // loading state — see OpeningRepository's cache.
        repository.cached(play)?.let { return show(it) }

        // Nothing to dim on the very first lookup, so that one reads as
        // loading; afterwards the previous position's numbers stay up, dimmed,
        // until the answer arrives (DESIGN.md 9.5절).
        _uiState.update {
            val hasSomethingToDim = !it.isLoading && !it.isError
            it.copy(isLoading = !hasSomethingToDim, isStale = hasSomethingToDim, isError = false)
        }

        lookupJob = viewModelScope.launch {
            repository.explore(play)
                .onSuccess { response -> show(response) }
                .onFailure { _uiState.update { s -> s.copy(isLoading = false, isStale = false, isError = true) } }
        }
    }

    private fun show(response: ExplorerResponse) {
        _uiState.update {
            it.copy(
                opening = response.opening,
                white = response.white,
                draws = response.draws,
                black = response.black,
                moves = response.moves,
                candidates = candidateRanks(response.moves),
                isLoading = false,
                isStale = false,
                isError = false,
            )
        }
    }

    /**
     * The top few moves as the board draws them. `moves` arrives sorted by
     * popularity, so the index is the rank. Two moves can share a destination
     * (two knights reaching the same square); the more popular one keeps it,
     * and the other stays list-only — which is why the board's numbers can skip.
     */
    private fun candidateRanks(moves: List<ExplorerMove>): List<CandidateMove> {
        val taken = mutableSetOf<Square>()
        return moves.take(OpeningDimens.CandidateMarkerLimit).mapIndexedNotNull { index, move ->
            val from = move.origin() ?: return@mapIndexedNotNull null
            val to = move.destination() ?: return@mapIndexedNotNull null
            if (!taken.add(to)) return@mapIndexedNotNull null
            CandidateMove(rank = index + 1, from = from, to = to)
        }
    }
}

/**
 * The squares a candidate move runs between. Read straight off the UCI string
 * rather than by constructing a chesslib `Move`, so a promotion ("e7e8q") reads
 * the same as any other move.
 */
private fun ExplorerMove.origin(): Square? = squareAt(uci, 0)

private fun ExplorerMove.destination(): Square? = squareAt(uci, 2)

private fun squareAt(uci: String, index: Int): Square? =
    runCatching { Square.fromValue(uci.substring(index, index + 2).uppercase()) }.getOrNull()
