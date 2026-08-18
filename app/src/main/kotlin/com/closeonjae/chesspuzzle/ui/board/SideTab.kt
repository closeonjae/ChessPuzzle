package com.closeonjae.chesspuzzle.ui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.closeonjae.chesspuzzle.ui.theme.Accent
import com.closeonjae.chesspuzzle.ui.theme.Dimens
import com.closeonjae.chesspuzzle.ui.theme.Surface

/** Icon size inside a side tab — the tab is only 17dp wide, so the glyph has to clear its curved edge. */
private val TabIconSize = 14.dp

/** How far a disabled tab fades — the design mockup's `opacity .22` (DESIGN.md 9.4절). */
private const val DISABLED_ALPHA = 0.22f

/**
 * One of the two half-moon tabs flanking the board (DESIGN.md 4/9절). The
 * puzzle screen's hint/keyboard/review tabs and the opening screen's
 * undo/list tabs are all this same slot, size and silhouette — only the icon
 * and the action differ.
 *
 * A disabled tab fades *and* drops its click handler: unlike the puzzle
 * screen's review arrows (which keep theirs so a dead-end tap can't fall
 * through to the tap-anywhere handler underneath), the opening screen has no
 * such whole-screen gesture, so there is nothing for a dropped tap to leak into.
 */
@Composable
fun SideTab(
    boardSide: Dp,
    shape: Shape,
    iconRes: Int,
    onTapped: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Accent,
) {
    Box(
        modifier = modifier
            .size(width = Dimens.KeyboardTabWidth, height = boardSide * Dimens.KeyboardTabHeightRatio)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(Surface)
            .then(if (enabled) Modifier.clickable(onClick = onTapped) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(TabIconSize),
        )
    }
}
