package com.closeonjae.chesspuzzle

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closeonjae.chesspuzzle.login.LoginScreen
import com.closeonjae.chesspuzzle.login.LoginViewModel
import com.closeonjae.chesspuzzle.mode.AppMode
import com.closeonjae.chesspuzzle.mode.ModeScreen
import com.closeonjae.chesspuzzle.opening.OpeningScreen
import com.closeonjae.chesspuzzle.opening.OpeningViewModel
import com.closeonjae.chesspuzzle.puzzle.PuzzleScreen
import com.closeonjae.chesspuzzle.puzzle.PuzzleViewModel
import com.closeonjae.chesspuzzle.ui.theme.ChessPuzzleTheme

/** How long the screen stays fully on (no ambient dim) after launch — user request. */
private const val KEEP_SCREEN_ON_MS = 3 * 60 * 1000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A puzzle can take a while to think through — keep the screen lit
        // for a few minutes rather than letting the watch's usual (much
        // shorter) timeout dim it mid-thought, then release the flag so it
        // goes back to normal behavior instead of staying on forever.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Handler(Looper.getMainLooper()).postDelayed(
            { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) },
            KEEP_SCREEN_ON_MS,
        )

        val app = application as ChessPuzzleApp

        setContent {
            ChessPuzzleTheme {
                // Whichever the DataStore-backed token flow currently holds decides the
                // screen — signed out goes to login, signed in to the mode picker.
                // Momentarily null while DataStore's first read completes, same as "signed out".
                val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)

                if (accessToken == null) {
                    val loginViewModel: LoginViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { LoginViewModel(app.authManager) }
                        },
                    )
                    LoginScreen(viewModel = loginViewModel)
                } else {
                    SignedInContent(app)
                }
            }
        }
    }
}

/**
 * Mode picker plus the two modes (PLAN.md 9.2절). Still no nav graph: three
 * screens in a star, not a stack.
 *
 * Wear turns the edge swipe-to-dismiss gesture into a back press, so
 * [BackHandler] covers both it and the hardware back — swiping out of a mode
 * returns to the picker instead of leaving the app. The trade-off versus
 * `SwipeToDismissBox` is that the swipe doesn't drag the screen along with the
 * finger; it just goes back when the gesture completes.
 *
 * The mode lives in `remember`, but the ViewModels behind each mode do not —
 * `viewModel()` scopes them to the activity's own store, so leaving a puzzle
 * for the opening explorer and coming back finds the puzzle exactly as it was.
 */
@Composable
private fun SignedInContent(app: ChessPuzzleApp) {
    var mode by remember { mutableStateOf<AppMode?>(null) }

    BackHandler(enabled = mode != null) { mode = null }

    when (mode) {
        null -> ModeScreen(
            onPuzzles = { mode = AppMode.PUZZLES },
            onOpenings = { mode = AppMode.OPENINGS },
        )

        AppMode.PUZZLES -> {
            val puzzleViewModel: PuzzleViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PuzzleViewModel(app.puzzleRepository) }
                },
            )
            PuzzleScreen(viewModel = puzzleViewModel)
        }

        AppMode.OPENINGS -> {
            val openingViewModel: OpeningViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { OpeningViewModel(app.openingRepository) }
                },
            )
            OpeningScreen(viewModel = openingViewModel)
        }
    }
}
