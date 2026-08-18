package com.closeonjae.chesspuzzle.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.closeonjae.chesspuzzle.ui.theme.Accent
import com.closeonjae.chesspuzzle.ui.theme.AppType
import com.closeonjae.chesspuzzle.ui.theme.Background
import com.closeonjae.chesspuzzle.ui.theme.Dimens
import com.closeonjae.chesspuzzle.ui.theme.Surface

/** Which side of the app is open. Null (in [com.closeonjae.chesspuzzle.MainActivity]) means this picker is showing. */
enum class AppMode { PUZZLES, OPENINGS }

/**
 * What the app opens on once signed in (DESIGN.md 9.4절). One job — pick a
 * side — so the screen is two buttons and nothing else, in the login screen's
 * own button shape and size so there is no new control to learn.
 *
 * Puzzles sits on top in the filled accent treatment because it is the app's
 * existing main flow; openings takes the outlined variant. Either is one tap
 * away, and back returns here.
 */
@Composable
fun ModeScreen(onPuzzles: () -> Unit, onOpenings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPuzzles,
                modifier = Modifier.fillMaxWidth(0.7f).height(Dimens.ButtonHeight),
                shape = RoundedCornerShape(Dimens.ButtonCornerRadius),
            ) {
                Text(
                    text = "Puzzles",
                    style = AppType.buttonLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = onOpenings,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(Dimens.ButtonHeight)
                    .border(1.dp, Accent, RoundedCornerShape(Dimens.ButtonCornerRadius)),
                shape = RoundedCornerShape(Dimens.ButtonCornerRadius),
                colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = Accent),
            ) {
                Text(
                    text = "Openings",
                    style = AppType.buttonLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
