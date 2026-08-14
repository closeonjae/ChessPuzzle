package com.closeonjae.chesspuzzle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closeonjae.chesspuzzle.auth.TokenStore
import com.closeonjae.chesspuzzle.core.lichess.Game
import com.closeonjae.chesspuzzle.core.lichess.LichessApiClient
import com.closeonjae.chesspuzzle.core.lichess.Perf
import com.closeonjae.chesspuzzle.core.lichess.Player
import com.closeonjae.chesspuzzle.core.lichess.Puzzle
import com.closeonjae.chesspuzzle.core.lichess.PuzzleAndGame
import com.closeonjae.chesspuzzle.core.lichess.PuzzleBatchSolveResponse
import com.closeonjae.chesspuzzle.core.lichess.PuzzleGlicko
import com.closeonjae.chesspuzzle.core.lichess.PuzzleRound
import com.closeonjae.chesspuzzle.data.PuzzleRepository
import com.closeonjae.chesspuzzle.puzzle.PuzzleScreen
import com.closeonjae.chesspuzzle.puzzle.PuzzleViewModel
import com.closeonjae.chesspuzzle.ui.theme.ChessPuzzleTheme

/**
 * Debug-only entry point (see app/src/debug/AndroidManifest.xml) for
 * screen-checking PuzzleScreen without a completed Lichess sign-in — the
 * real RemoteAuthClient flow needs a paired phone (RESEARCH.md 4절), which
 * a standalone emulator/CI run doesn't have.
 *
 * The puzzle itself (Ruy Lopez: 1.e4 e5 2.Nf3 Nc6 3.Bb5 a6 4.Ba4 Nf6) is the
 * same fixture already exercised by :core's PuzzleEngineTest — real legal
 * moves, not placeholder data.
 *
 * Launch with: adb shell am start -a com.closeonjae.chesspuzzle.PREVIEW_PUZZLE
 */
class DebugPuzzlePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = FakePuzzleRepository(
            LichessApiClient(clientId = "debug-preview"),
            TokenStore(this),
        )
        setContent {
            ChessPuzzleTheme {
                val viewModel: PuzzleViewModel = viewModel(
                    factory = viewModelFactory { initializer { PuzzleViewModel(repository) } },
                )
                PuzzleScreen(viewModel = viewModel)
            }
        }
    }
}

private class FakePuzzleRepository(
    api: LichessApiClient,
    tokenStore: TokenStore,
) : PuzzleRepository(api, tokenStore) {

    // Fresh id per call: PuzzleViewModel now refuses to re-show the puzzle
    // currently on screen (production fix for the repeated-puzzle bug), and
    // this fixture is the one legitimate "same puzzle again" source — a new
    // id each time keeps the preview advancing through the same position.
    private var serial = 0

    override suspend fun nextPuzzle(): Result<PuzzleAndGame> = Result.success(
        PuzzleAndGame(
            game = Game(
                id = "debugPreview",
                perf = Perf(key = "blitz", name = "Blitz"),
                rated = true,
                players = listOf(
                    Player(name = "White", color = "white", rating = 1650),
                    Player(name = "Black", color = "black", rating = 1620),
                ),
                pgn = "e4 e5 Nf3 Nc6 Bb5",
                clock = "5+1",
            ),
            puzzle = Puzzle(
                id = "debugPreview${serial++}",
                rating = 1632,
                plays = 0,
                solution = listOf("a7a6", "b5a4", "g8f6"),
                themes = listOf("opening"),
                initialPly = 5,
            ),
        ),
    )

    // Mirrors real Lichess behavior enough to screen-check the hint/wrong-
    // move rating logic (PuzzleViewModel.handleOutcome) without needing a
    // real login: a reported win nudges the rating up, a loss nudges it
    // down, instead of always returning the same canned +14.
    override suspend fun reportSolved(puzzleId: String, win: Boolean): Result<PuzzleBatchSolveResponse> {
        val diff = if (win) 14 else -6
        return Result.success(
            PuzzleBatchSolveResponse(
                glicko = PuzzleGlicko(rating = 1632.0 + diff, deviation = 80.0),
                rounds = listOf(PuzzleRound(id = puzzleId, win = win, ratingDiff = diff)),
            ),
        )
    }
}
