package com.closeonjae.chesspuzzle

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closeonjae.chesspuzzle.login.LoginScreen
import com.closeonjae.chesspuzzle.login.LoginViewModel
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
                // screen — no separate nav graph needed for two screens (PLAN.md 3절).
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
                    val puzzleViewModel: PuzzleViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { PuzzleViewModel(app.puzzleRepository) }
                        },
                    )
                    PuzzleScreen(viewModel = puzzleViewModel)
                }
            }
        }
    }
}
