package com.closeonjae.chesspuzzle.puzzle

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closeonjae.chesspuzzle.core.puzzle.MoveOutcome
import com.closeonjae.chesspuzzle.core.puzzle.PuzzleEngine
import com.closeonjae.chesspuzzle.core.puzzle.toPuzzleData
import com.closeonjae.chesspuzzle.data.PuzzleRepository
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** DESIGN.md 5절 states: default (turn label), WRONG (dims the board, waits for a Retry tap), SOLVED (brief success flash). */
enum class MoveFeedback { NONE, WRONG, SOLVED }

/** How long a piece takes to slide from one square to another (user request) — PuzzleScreen's Board() animates to this, and this file times the opponent-reply animation to start right after the solver's own finishes. */
const val MOVE_ANIMATION_MS = 220L

data class PuzzleUiState(
    val engine: PuzzleEngine? = null,
    val rating: Int? = null,
    val ratingDelta: Int? = null,
    val selectedSquare: Square? = null,
    /** Set by a first hint-button tap: the square whose piece the solver needs to move. */
    val hintSquare: Square? = null,
    /** The move (solver's own, or the auto-played opponent reply) the board should currently be animating (user request) — not necessarily reflecting where the piece already sits in `engine.board`, which updates instantly. */
    val animatedMove: Move? = null,
    /**
     * Non-null exactly while [feedback] is [MoveFeedback.WRONG]: the move
     * that was actually attempted (user request — even a wrong move should
     * visibly travel to its destination before rolling back). The board
     * itself was already reverted by [PuzzleEngine], so this is the only
     * record of where the piece was "trying" to go.
     */
    val wrongAttempt: Move? = null,
    /** How many times the hint button has been checked on this puzzle (user request — counted on the first tap of each look, whether or not it's then used to actually play the move). Resets to 0 on a new puzzle. */
    val hintUsedCount: Int = 0,
    /** Whether any wrong move has been made on this puzzle attempt (user request — resets to false on a new puzzle). Both this and [hintUsedCount] feed into whether the solve gets reported to Lichess as a win (DESIGN.md 5절). */
    val hadWrongAttempt: Boolean = false,
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
                animatedMove = null,
                wrongAttempt = null,
                hintUsedCount = 0,
                hadWrongAttempt = false,
                feedback = MoveFeedback.NONE,
            )
        }
        viewModelScope.launch {
            repository.nextPuzzle()
                // PuzzleEngine's init replays/validates the puzzle's own PGN
                // and solution moves and throws on anything unexpected there
                // (RESEARCH.md 8절) — mapCatching keeps a malformed puzzle
                // from that being an *uncaught* exception that crashes the
                // whole app (real-device report: a few Retry taps after
                // "Connection Lost" eventually force-closed the app), routing
                // it into the same error state as any other fetch failure.
                // Logged here (before construction can throw) so a future
                // real-puzzle failure can be diagnosed from `adb logcat`
                // instead of guessed at (DESIGN.md 5절 힌트 버그 기록).
                .mapCatching {
                    Log.d(
                        "PuzzleViewModel",
                        "puzzle=${it.puzzle.id} initialPly=${it.puzzle.initialPly} " +
                            "gamePgn=\"${it.game.pgn}\" solution=${it.puzzle.solution}",
                    )
                    PuzzleEngine(it.toPuzzleData()) to it.puzzle.rating
                }
                .onSuccess { (engine, rating) ->
                    _uiState.update {
                        it.copy(engine = engine, rating = rating, ratingDelta = null, isLoading = false)
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
            else -> attemptSafely(engine) { it.attemptCoordinates(selected, square) }
        }
    }

    /** SAN entered via the keyboard-entry tab (DESIGN.md 5절, RESEARCH.md 10-A절). */
    fun onSanEntered(san: String) {
        val state = _uiState.value
        val engine = state.engine ?: return
        if (state.isLoading || engine.isSolved || san.isBlank()) return
        if (state.hintSquare != null) _uiState.update { it.copy(hintSquare = null) }
        attemptSafely(engine) { it.attemptSan(san.trim()) }
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
            // Counted here, on the "reveal" tap, not the "play" tap (user
            // request: rating should reflect how many times the hint was
            // *checked*, whether or not it then got used to move).
            _uiState.update {
                it.copy(hintSquare = from, selectedSquare = null, hintUsedCount = it.hintUsedCount + 1)
            }
        } else {
            val move = engine.hintMove ?: return
            _uiState.update { it.copy(hintSquare = null) }
            attemptSafely(engine) { it.attemptCoordinates(move.from, move.to) }
        }
    }

    /**
     * Runs a move attempt against [engine], catching anything it throws
     * instead of letting it crash the app. Real-device crash log:
     * `PuzzleEngine.attempt()`'s internal `check()` on the auto-played
     * opponent reply threw `IllegalStateException` straight out of a UI tap
     * handler, with nothing between it and the app dying — a puzzle-data
     * problem (root-caused and fixed separately, DESIGN.md 5절), but a
     * *move attempt* should never be able to take the whole app down over
     * one puzzle's data either way, the same reasoning `loadNextPuzzle()`'s
     * `mapCatching` already applies to fetching a puzzle in the first place.
     */
    private fun attemptSafely(engine: PuzzleEngine, attempt: (PuzzleEngine) -> MoveOutcome) {
        val outcome = try {
            attempt(engine)
        } catch (e: IllegalStateException) {
            _uiState.update { it.copy(selectedSquare = null, error = e.message ?: "Move failed") }
            return
        }
        handleOutcome(engine, outcome)
    }

    private fun handleOutcome(engine: PuzzleEngine, outcome: MoveOutcome) {
        when (outcome) {
            MoveOutcome.IllegalMove -> _uiState.update { it.copy(selectedSquare = null) }
            is MoveOutcome.WrongMove -> _uiState.update {
                it.copy(
                    selectedSquare = null,
                    feedback = MoveFeedback.WRONG,
                    wrongAttempt = outcome.attempted,
                    hadWrongAttempt = true,
                )
            }
            is MoveOutcome.Correct -> {
                _uiState.update {
                    it.copy(selectedSquare = null, feedback = MoveFeedback.NONE, animatedMove = outcome.solverMove)
                }
                // The opponent's reply already happened on the board by now
                // (PuzzleEngine plays both moves before returning) — delayed
                // just long enough for the solver's own move to finish
                // animating first, so the two don't overlap (user request:
                // a real "I move, then they move" feel, not both at once).
                outcome.opponentReply?.let { reply ->
                    viewModelScope.launch {
                        delay(MOVE_ANIMATION_MS)
                        _uiState.update { it.copy(animatedMove = reply) }
                    }
                }
            }
            is MoveOutcome.Solved -> {
                // Lichess's own API only takes a win/lose bool for a solve —
                // there's no "partial credit" endpoint to scale a rating
                // *amount* by (user request was "레이팅 증감을 설정" — the closest
                // honest match within that constraint). Any hint check or
                // wrong move along the way reports the solve as a loss
                // (rating goes down, same as lichess.org/training already
                // does for "view solution"/a wrong try) instead of a win.
                val state = _uiState.value
                val win = state.hintUsedCount == 0 && !state.hadWrongAttempt
                _uiState.update {
                    val base = it.copy(selectedSquare = null, feedback = MoveFeedback.SOLVED)
                    if (outcome.solverMove != null) base.copy(animatedMove = outcome.solverMove) else base
                }
                reportSolvedAndAdvance(engine.id, win)
            }
        }
    }

    private fun reportSolvedAndAdvance(puzzleId: String, win: Boolean) {
        viewModelScope.launch {
            repository.reportSolved(puzzleId, win = win).onSuccess { response ->
                val diff = response.rounds.firstOrNull()?.ratingDiff
                // win=false here means a hint was checked and/or a wrong
                // move was made this attempt (see handleOutcome) — kept as
                // a log, not just a comment, since it's otherwise invisible
                // whether a given solve was actually reported as a loss.
                Log.d("PuzzleViewModel", "reportSolved(win=$win) -> ratingDiff=$diff")
                response.glicko?.let { glicko ->
                    _uiState.update { it.copy(rating = glicko.rating.toInt(), ratingDelta = diff) }
                }
            }
            // Hold the "Correct" state briefly (DESIGN.md 5절: ~0.8s) before auto-advancing.
            delay(800)
            loadNextPuzzle()
        }
    }

    /**
     * Dismisses a WRONG state back to the normal turn label — the wrong
     * move was already undone, this is just "let me try again" (user
     * request: an explicit tap-anywhere-on-screen, not an auto-timeout).
     * Clearing [PuzzleUiState.wrongAttempt] here (rather than leaving it)
     * is what Board() watches to know it's time to animate the piece
     * rolling back (user request) — it remembers the last value locally
     * before it goes null.
     */
    fun clearWrongFeedback() {
        _uiState.update {
            if (it.feedback == MoveFeedback.WRONG) it.copy(feedback = MoveFeedback.NONE, wrongAttempt = null) else it
        }
    }

    private fun isOwnPiece(engine: PuzzleEngine, square: Square): Boolean {
        val piece = engine.board.getPiece(square)
        return piece != Piece.NONE && piece.pieceSide == engine.sideToMove
    }
}
