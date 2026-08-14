package com.closeonjae.chesspuzzle.puzzle

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closeonjae.chesspuzzle.core.lichess.PuzzleAndGame
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

/** DESIGN.md 5절 states: default (turn label), WRONG (dims the board, waits for a Retry tap), SOLVED (dims the board, holds the finished position until [PuzzleViewModel.onSolvedTapped] moves on — user request: no more auto-advance). */
enum class MoveFeedback { NONE, WRONG, SOLVED }

/** How long a piece takes to slide from one square to another (user request) — PuzzleScreen's Board() animates to this, and this file times the opponent-reply animation to start right after the solver's own finishes. */
const val MOVE_ANIMATION_MS = 220L

/** Extra pause after the solver's own move finishes animating before the opponent's reply starts moving (user request) — a visible beat between "I moved" and "they moved" instead of the two animations chaining back-to-back. */
const val OPPONENT_REPLY_PAUSE_MS = 100L

data class PuzzleUiState(
    val engine: PuzzleEngine? = null,
    /**
     * The solver's own puzzle Glicko rating (user report — this used to be
     * overwritten with each newly-loaded *puzzle's own* difficulty rating
     * instead, e.g. "1855", making the number jump around between
     * unrelated values every puzzle instead of tracking the solver's
     * actual rating, and making [ratingDelta] look inconsistent with it
     * — e.g. rating showing "1924 +13" when 13 puzzles back it was
     * nowhere near 1911). Only ever set from [PuzzleRepository.reportSolved]'s
     * `glicko.rating` response — null (chip hidden, RatingChip) until the
     * very first solve, since Lichess's puzzle-batch GET doesn't return
     * the solver's own rating, only each puzzle's.
     */
    val rating: Int? = null,
    /** The change from the solve that produced the current [rating] (user request) — cleared back to null on loading/advancing to a new puzzle, so it doesn't linger past the solve it belongs to. */
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
    /**
     * The opponent's auto-reply, from the moment the solver's own correct
     * move is played until [OPPONENT_REPLY_PAUSE_MS] later, when it's
     * actually revealed via [animatedMove] (user request — a paused beat
     * before it moves). [PuzzleEngine] already plays this on `engine.board`
     * synchronously, well before that reveal — PuzzleScreen's Board() uses
     * this to keep rendering the piece stationary at its *origin* square
     * during the pause, rather than reading `engine.board` unsuppressed and
     * showing it having already arrived (real bug: 상대 기물이 순간적으로 미리
     * 움직였다가 다시 순간적으로 돌아가서 애니메이션이 재생됨).
     */
    val pendingOpponentReply: Move? = null,
    /** How many times the hint button has been checked on this puzzle (user request — counted on the first tap of each look, whether or not it's then used to actually play the move). Resets to 0 on a new puzzle. */
    val hintUsedCount: Int = 0,
    /** Whether any wrong move has been made on this puzzle attempt (user request — resets to false on a new puzzle). Both this and [hintUsedCount] feed into whether the solve gets reported to Lichess as a win (DESIGN.md 5절). */
    val hadWrongAttempt: Boolean = false,
    val feedback: MoveFeedback = MoveFeedback.NONE,
    val isLoading: Boolean = true,
    val error: String? = null,
    /**
     * The next puzzle, fetched silently in the background as soon as this
     * one is solved (user request — the solved board stays on screen,
     * untouched, until the solver taps to move on; no more auto-advance).
     * Null until that background fetch finishes; consumed by
     * [PuzzleViewModel.advanceToNextPuzzle].
     */
    val nextEngine: PuzzleEngine? = null,
    /** The background fetch of [nextEngine] failed (user request: tapping a solved puzzle whose next puzzle failed to load shows a Retry button instead of doing nothing). */
    val nextPuzzleError: Boolean = false,
    /** Set by a tap on a solved board before [nextEngine] is ready (user request) — shows a loading spinner (or, if [nextPuzzleError] is also true, a Retry button) instead of the tap looking ignored; the fetch finishing on its own then finishes the advance. */
    val awaitingNextPuzzle: Boolean = false,
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
                pendingOpponentReply = null,
                hintUsedCount = 0,
                hadWrongAttempt = false,
                feedback = MoveFeedback.NONE,
                // ratingDelta clears (belonged to whatever was just solved,
                // if anything) — rating itself is untouched, since it's the
                // solver's own rating, not this puzzle's (see PuzzleUiState.rating).
                ratingDelta = null,
                nextEngine = null,
                nextPuzzleError = false,
                awaitingNextPuzzle = false,
            )
        }
        viewModelScope.launch {
            fetchPuzzle()
                .onSuccess { engine ->
                    _uiState.update { it.copy(engine = engine, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Connection failed") }
                }
        }
    }

    /**
     * Wraps one fetched puzzle in a [PuzzleEngine] — shared by every path
     * that ends up with a [PuzzleAndGame], whether from its own GET
     * ([fetchPuzzle]) or bundled into a solve POST's response
     * ([reportSolvedAndPrefetchNext]).
     *
     * PuzzleEngine's init replays/validates the puzzle's own PGN and
     * solution moves and throws on anything unexpected there (RESEARCH.md
     * 8절) — the caller wraps this in `runCatching`/`mapCatching` so a
     * malformed puzzle can't be an *uncaught* exception that crashes the
     * whole app (real-device report: a few Retry taps after "Connection
     * Lost" eventually force-closed the app), routing it into the same
     * error state as any other fetch failure. Logged here (before
     * construction can throw) so a future real-puzzle failure can be
     * diagnosed from `adb logcat` instead of guessed at (DESIGN.md 5절
     * 힌트 버그 기록).
     */
    private fun buildEngine(puzzleAndGame: PuzzleAndGame): PuzzleEngine {
        Log.d(
            "PuzzleViewModel",
            "puzzle=${puzzleAndGame.puzzle.id} initialPly=${puzzleAndGame.puzzle.initialPly} " +
                "gamePgn=\"${puzzleAndGame.game.pgn}\" solution=${puzzleAndGame.puzzle.solution}",
        )
        return PuzzleEngine(puzzleAndGame.toPuzzleData())
    }

    /**
     * Fetches one puzzle with its own network round trip and wraps it —
     * used for the very first puzzle ([loadNextPuzzle]) and as the fallback
     * when a solve response doesn't come bundled with the next one
     * ([reportSolvedAndPrefetchNext]'s fallback, [retryNextPuzzle]).
     */
    private suspend fun fetchPuzzle(): Result<PuzzleEngine> =
        repository.nextPuzzle().mapCatching { buildEngine(it) }

    /**
     * Applies a fetched-next-puzzle result the same way regardless of where
     * it came from — a fresh GET or bundled into a solve response.
     *
     * Guards against the exact bug the bundled-response scheme was meant to
     * prevent (user report: solving a puzzle rolled back into the same one
     * again) — it can still resurface however "next" ends up fetched, so
     * this checks it here, in the one place both paths funnel through,
     * rather than only where the bundled response is read. If what came
     * back is somehow the very puzzle already showing (already solved,
     * still on screen), it's refused and retried once more with a fresh GET
     * — bounded ([retriesLeft]) so a persistent repeat can't loop forever;
     * if the retry *also* comes back the same, it's shown anyway rather
     * than leaving the solver stuck. Logged plainly either way (not inside
     * a `.let`) so a recurrence has a trace to diagnose from in
     * `adb logcat` — the original report had none.
     */
    private fun applyNextPuzzleResult(result: Result<PuzzleEngine>, retriesLeft: Int = 1) {
        result
            .onSuccess { engine ->
                val currentId = _uiState.value.engine?.id
                if (engine.id == currentId) {
                    if (retriesLeft > 0) {
                        Log.d(
                            "PuzzleViewModel",
                            "next-puzzle fetch returned the same puzzle ($currentId) again — retrying",
                        )
                        fetchNextPuzzleInBackground(retriesLeft - 1)
                        return@onSuccess
                    }
                    Log.d(
                        "PuzzleViewModel",
                        "next-puzzle fetch still returned the same puzzle ($currentId) after retrying — showing it anyway",
                    )
                }
                _uiState.update { it.copy(nextEngine = engine, nextPuzzleError = false) }
                // The solver already tapped once and is waiting on this
                // exact fetch (PuzzleUiState.awaitingNextPuzzle) — finish
                // the advance now instead of making them tap again.
                if (_uiState.value.awaitingNextPuzzle) advanceToNextPuzzle()
            }
            .onFailure {
                _uiState.update { it.copy(nextPuzzleError = true) }
            }
    }

    /**
     * Loads the next puzzle in the background with its own GET — the
     * fallback path when a solve response didn't already bundle one (the
     * debug fixture, or a solve report that itself failed), what
     * [retryNextPuzzle] retries with, and what [applyNextPuzzleResult]
     * itself retries with once if that result is a same-puzzle repeat.
     */
    private fun fetchNextPuzzleInBackground(retriesLeft: Int = 1) {
        viewModelScope.launch { applyNextPuzzleResult(fetchPuzzle(), retriesLeft) }
    }

    /** Retry button on a solved puzzle whose background next-puzzle fetch failed (user request). */
    fun retryNextPuzzle() {
        _uiState.update { it.copy(nextPuzzleError = false) }
        fetchNextPuzzleInBackground()
    }

    /**
     * Tap-anywhere to move on from a solved puzzle (user request — mirrors
     * [clearWrongFeedback]'s tap-anywhere for a wrong move). The next
     * puzzle has usually already finished loading in the background by now
     * ([reportSolvedAndPrefetchNext]), so this normally swaps to it right
     * away; if it hasn't yet, this just arms [PuzzleUiState.awaitingNextPuzzle]
     * so the fetch completing on its own finishes the swap, while the
     * screen shows a loading spinner (or a Retry button, if that fetch
     * already failed) instead of the tap looking ignored.
     */
    fun onSolvedTapped() {
        val state = _uiState.value
        if (state.feedback != MoveFeedback.SOLVED) return
        if (state.nextEngine != null) advanceToNextPuzzle() else _uiState.update { it.copy(awaitingNextPuzzle = true) }
    }

    /** Swaps the solved board for the already-fetched [PuzzleUiState.nextEngine] (fresh per-attempt fields reset, same as [loadNextPuzzle]). */
    private fun advanceToNextPuzzle() {
        val state = _uiState.value
        val engine = state.nextEngine ?: return
        _uiState.update {
            it.copy(
                engine = engine,
                // ratingDelta clears (belonged to the just-solved puzzle) —
                // rating itself is untouched, it's the solver's own rating,
                // not this puzzle's (see PuzzleUiState.rating).
                ratingDelta = null,
                selectedSquare = null,
                hintSquare = null,
                animatedMove = null,
                wrongAttempt = null,
                pendingOpponentReply = null,
                hintUsedCount = 0,
                hadWrongAttempt = false,
                feedback = MoveFeedback.NONE,
                nextEngine = null,
                nextPuzzleError = false,
                awaitingNextPuzzle = false,
            )
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
            // hintSquare is cleared by attemptSafely/handleOutcome below
            // (every outcome branch resets it) rather than pre-emptively
            // here — folds the hint-square clear into the same state
            // update as the move's own outcome instead of two separate
            // back-to-back updates, so a hint-triggered move's animation
            // trigger looks exactly like any other move's (user request:
            // hint should animate too).
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
            _uiState.update { it.copy(selectedSquare = null, hintSquare = null, error = e.message ?: "Move failed") }
            return
        }
        handleOutcome(engine, outcome)
    }

    private fun handleOutcome(engine: PuzzleEngine, outcome: MoveOutcome) {
        when (outcome) {
            MoveOutcome.IllegalMove -> _uiState.update { it.copy(selectedSquare = null, hintSquare = null) }
            is MoveOutcome.WrongMove -> _uiState.update {
                it.copy(
                    selectedSquare = null,
                    hintSquare = null,
                    feedback = MoveFeedback.WRONG,
                    wrongAttempt = outcome.attempted,
                    hadWrongAttempt = true,
                )
            }
            is MoveOutcome.Correct -> {
                _uiState.update {
                    it.copy(
                        selectedSquare = null,
                        hintSquare = null,
                        feedback = MoveFeedback.NONE,
                        animatedMove = outcome.solverMove,
                        // Set synchronously, in this same update, even
                        // though it isn't *revealed* (animatedMove) until
                        // the delay below — PuzzleScreen's Board() needs to
                        // know right away which squares belong to a still-
                        // pending reply, so it can keep rendering that piece
                        // at its origin instead of reading engine.board
                        // (already mutated) and showing it having arrived
                        // early (see PuzzleUiState.pendingOpponentReply).
                        pendingOpponentReply = outcome.opponentReply,
                    )
                }
                // The opponent's reply already happened on the board by now
                // (PuzzleEngine plays both moves before returning) — delayed
                // until the solver's own move finishes animating, plus a
                // further beat (OPPONENT_REPLY_PAUSE_MS, user request), so
                // the two don't overlap and don't chain instantly either —
                // a real "I move, then [pause] they move" feel.
                outcome.opponentReply?.let { reply ->
                    viewModelScope.launch {
                        delay(MOVE_ANIMATION_MS + OPPONENT_REPLY_PAUSE_MS)
                        _uiState.update { it.copy(animatedMove = reply, pendingOpponentReply = null) }
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
                    val base = it.copy(selectedSquare = null, hintSquare = null, feedback = MoveFeedback.SOLVED)
                    if (outcome.solverMove != null) base.copy(animatedMove = outcome.solverMove) else base
                }
                reportSolvedAndPrefetchNext(engine.id, win)
            }
        }
    }

    /**
     * Reports the solve to Lichess (rating update) and loads the *next*
     * puzzle silently in the background (user request) — the solved board
     * now stays on screen exactly as it finished, with no auto-advance and
     * no timer, until [onSolvedTapped] moves on, so there's no reason to
     * wait before starting this.
     *
     * The next puzzle normally comes back bundled in this same solve
     * response ([PuzzleRepository.reportSolved] requests it with
     * `nextBatchCount = 1`) instead of a separate GET — one network round
     * trip instead of two (user report: next-puzzle loading was too slow),
     * and — more importantly — it can only ever be a puzzle the server has
     * *already* recorded this solve against. A separate GET fired
     * concurrently with the solve POST (the previous approach, prefetching
     * eagerly) could race ahead of the server recording the solve and get
     * handed back the very puzzle just solved, still marked unsolved (user
     * report: solving a puzzle rolled back into solving the same one
     * again). [fetchNextPuzzleInBackground] is still the fallback for when
     * nothing comes back bundled — the debug fixture, or a solve report
     * that itself failed.
     */
    private fun reportSolvedAndPrefetchNext(puzzleId: String, win: Boolean) {
        viewModelScope.launch {
            val result = repository.reportSolved(puzzleId, win = win)
            result.onSuccess { response ->
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
            val bundledNext = result.getOrNull()?.puzzles?.firstOrNull()
            if (bundledNext != null) {
                applyNextPuzzleResult(runCatching { buildEngine(bundledNext) })
            } else {
                fetchNextPuzzleInBackground()
            }
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
