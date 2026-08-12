package com.closeonjae.chesspuzzle.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Wraps content in the app's [MaterialTheme], overriding only the roles the
 * DESIGN.md palette actually specifies. The board itself is drawn with the
 * literal Board- and Piece-prefixed colors above, not through this color scheme — those
 * are deliberately fixed regardless of system theme (DESIGN.md 1절).
 */
@Composable
fun ChessPuzzleTheme(content: @Composable () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme.copy(
        primary = Accent,
        onPrimary = TextPrimary,
        background = Background,
        onBackground = TextPrimary,
        surfaceContainer = Surface,
        onSurface = TextPrimary,
        error = ErrorColor,
        onError = TextPrimary,
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
