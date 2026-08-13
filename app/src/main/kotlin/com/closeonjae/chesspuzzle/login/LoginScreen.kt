package com.closeonjae.chesspuzzle.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import com.closeonjae.chesspuzzle.ui.theme.AppType
import com.closeonjae.chesspuzzle.ui.theme.Background
import com.closeonjae.chesspuzzle.ui.theme.Dimens
import com.closeonjae.chesspuzzle.ui.theme.ErrorColor
import com.closeonjae.chesspuzzle.ui.theme.TextSecondary

/** DESIGN.md 5절 로그인 화면: default / in-progress / failed. English-only copy. */
@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "LICHESS PUZZLE", style = AppType.caption, color = TextSecondary)

            if (state.isSigningIn) {
                CircularProgressIndicator()
                Text(
                    text = "Complete sign-in on your phone",
                    style = AppType.caption,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Sized explicitly to DESIGN.md's button spec — 4절, same
                // values PuzzleScreen's Connection Lost retry button uses.
                Button(
                    onClick = viewModel::signIn,
                    modifier = Modifier.fillMaxWidth(0.7f).height(Dimens.ButtonHeight),
                    shape = RoundedCornerShape(Dimens.ButtonCornerRadius),
                ) {
                    Text(text = "Sign in with Lichess", style = AppType.buttonLabel)
                }
                if (state.error != null) {
                    Text(
                        text = "Sign-in failed. Tap to retry.",
                        style = AppType.caption,
                        color = ErrorColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}
