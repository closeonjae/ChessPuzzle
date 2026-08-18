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
    /** Destination square → popularity rank (1 = most played), for the board badges. Empty while a piece is selected. */
    val candidates: Map<Square, Int> = emptyMap(),
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
    val total: Int get() = white + draws + black
}

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
                state.candidates.containsKey(square) && candidateUci != null -> playUci(candidateUci)
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
     * Destination square → rank for the top few moves. `moves` arrives sorted
     * by popularity, so the index is the rank. Two moves can share a
     * destination (two knights reaching the same square); the more popular one
     * wins, since it got there first and `putIfAbsent` semantics apply.
     */
    private fun candidateRanks(moves: List<ExplorerMove>): Map<Square, Int> {
        val ranks = LinkedHashMap<Square, Int>()
        moves.take(OpeningDimens.CandidateMarkerLimit).forEachIndexed { index, move ->
            move.destination()?.let { ranks.putIfAbsent(it, index + 1) }
        }
        return ranks
    }
}

/**
 * The square a candidate move lands on. Taken from the UCI string's middle two
 * characters rather than by constructing a chesslib `Move`, so a promotion
 * ("e7e8q") reads the same as any other move.
 */
private fun ExplorerMove.destination(): Square? =
    runCatching { Square.fromValue(uci.substring(2, 4).uppercase()) }.getOrNull()
