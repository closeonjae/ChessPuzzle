package com.closeonjae.chesspuzzle

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
