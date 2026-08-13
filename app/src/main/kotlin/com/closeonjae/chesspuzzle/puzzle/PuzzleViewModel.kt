package com.closeonjae.chesspuzzle.puzzle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closeonjae.chesspuzzle.core.puzzle.MoveOutcome
import com.closeonjae.chesspuzzle.core.puzzle.PuzzleEngine
import com.closeonjae.chesspuzzle.core.puzzle.toPuzzleData
import com.closeonjae.chesspuzzle.data.PuzzleRepository
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** DESIGN.md 5절 states: default (turn label), WRONG (brief error flash), SOLVED (brief success flash). */
enum class MoveFeedback { NONE, WRONG, SOLVED }

data class PuzzleUiState(
    val engine: PuzzleEngine? = null,
    val rating: Int? = null,
    val ratingDelta: Int? = null,
    val selectedSquare: Square? = null,
    /** Set by a first hint-button tap: the square whose piece the solver needs to move. */
    val hintSquare: Square? = null,
    val feedback: MoveFeedback = MoveFeedback.NONE,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Drives one puzzle screen's worth of state: fetch → tap/SAN input → report → fetch next (PLAN.md 4절). */
class PuzzleViewModel(private val repository: PuzzleRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PuzzleUiState())
    val uiState: StateFlow<PuzzleUiState> = _uiState.asStateFlow()

    init {
        loadNextPuzzle()
    }

    fun loadNextPuzzle() {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                selectedSquare = null,
                hintSquare = null,
                feedback = MoveFeedback.NONE,
            )
        }
        viewModelScope.launch {
            repository.nextPuzzle()
                .onSuccess { puzzleAndGame ->
                    _uiState.update {
                        it.copy(
                            engine = PuzzleEngine(puzzleAndGame.toPuzzleData()),
                            rating = puzzleAndGame.puzzle.rating,
                            ratingDelta = null,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Connection failed") }
                }
        }
    }

    /** Tap-to-select-then-move (DESIGN.md 9절): first tap selects an own piece, second tap attempts the move. */
    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        val engine = state.engine ?: return
        if (state.isLoading || engine.isSolved) return
        if (state.hintSquare != null) _uiState.update { it.copy(hintSquare = null) }

        val selected = state.selectedSquare
        when {
            selected == null -> if (isOwnPiece(engine, square)) _uiState.update { it.copy(selectedSquare = square) }
            selected == square -> _uiState.update { it.copy(selectedSquare = null) }
            isOwnPiece(engine, square) -> _uiState.update { it.copy(selectedSquare = square) }
            else -> handleOutcome(engine, engine.attemptCoordinates(selected, square))
        }
    }

    /** SAN entered via the keyboard-entry tab (DESIGN.md 5절, RESEARCH.md 10-A절). */
    fun onSanEntered(san: String) {
        val state = _uiState.value
        val engine = state.engine ?: return
        if (state.isLoading || engine.isSolved || san.isBlank()) return
        if (state.hintSquare != null) _uiState.update { it.copy(hintSquare = null) }
        handleOutcome(engine, engine.attemptSan(san.trim()))
    }

    /**
     * Hint button (user request): first tap reveals which square's piece the
     * solver needs to move (highlighted, doesn't touch the board); a second
     * tap while that's showing plays the move for them, same as a normal
     * correct move (opponent auto-reply / puzzle-solved handling included).
     */
    fun onHintTapped() {
        val state = _uiState.value
        val engine = state.engine ?: return
        if (state.isLoading || engine.isSolved) return

        val armed = state.hintSquare
        if (armed == null) {
            val from = engine.hintMove?.from ?: return
            _uiState.update { it.copy(hintSquare = from, selectedSquare = null) }
        } else {
            val move = engine.hintMove ?: return
            _uiState.update { it.copy(hintSquare = null) }
            handleOutcome(engine, engine.attemptCoordinates(move.from, move.to))
        }
    }

    private fun handleOutcome(engine: PuzzleEngine, outcome: MoveOutcome) {
        when (outcome) {
            MoveOutcome.IllegalMove -> _uiState.update { it.copy(selectedSquare = null) }
            MoveOutcome.WrongMove -> _uiState.update { it.copy(selectedSquare = null, feedback = MoveFeedback.WRONG) }
            is MoveOutcome.Correct -> _uiState.update { it.copy(selectedSquare = null, feedback = MoveFeedback.NONE) }
            MoveOutcome.Solved -> {
                _uiState.update { it.copy(selectedSquare = null, feedback = MoveFeedback.SOLVED) }
                reportSolvedAndAdvance(engine.id)
            }
        }
    }

    private fun reportSolvedAndAdvance(puzzleId: String) {
        viewModelScope.launch {
            repository.reportSolved(puzzleId, win = true).onSuccess { response ->
                val diff = response.rounds.firstOrNull()?.ratingDiff
                response.glicko?.let { glicko ->
                    _uiState.update { it.copy(rating = glicko.rating.toInt(), ratingDelta = diff) }
                }
            }
            // Hold the "Correct" state briefly (DESIGN.md 5절: ~0.8s) before auto-advancing.
            delay(800)
            loadNextPuzzle()
        }
    }

    /** Clears a transient WRONG flash back to the normal turn label (PuzzleScreen calls this after a short delay). */
    fun clearWrongFeedback() {
        _uiState.update { if (it.feedback == MoveFeedback.WRONG) it.copy(feedback = MoveFeedback.NONE) else it }
    }

    private fun isOwnPiece(engine: PuzzleEngine, square: Square): Boolean {
        val piece = engine.board.getPiece(square)
        return piece != Piece.NONE && piece.pieceSide == engine.sideToMove
    }
}
