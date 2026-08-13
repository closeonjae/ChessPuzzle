package com.closeonjae.chesspuzzle.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import com.closeonjae.chesspuzzle.core.puzzle.PuzzleEngine
import com.closeonjae.chesspuzzle.input.rememberMoveInputLauncher
import com.closeonjae.chesspuzzle.ui.theme.Accent
import com.closeonjae.chesspuzzle.ui.theme.AppType
import com.closeonjae.chesspuzzle.ui.theme.Background
import com.closeonjae.chesspuzzle.ui.theme.BoardDark
import com.closeonjae.chesspuzzle.ui.theme.BoardLight
import com.closeonjae.chesspuzzle.ui.theme.Dimens
import com.closeonjae.chesspuzzle.ui.theme.ErrorColor
import com.closeonjae.chesspuzzle.ui.theme.SelectedSquare
import com.closeonjae.chesspuzzle.ui.theme.Success
import com.closeonjae.chesspuzzle.ui.theme.Surface
import com.closeonjae.chesspuzzle.ui.theme.TextSecondary
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.delay

/**
 * The puzzle screen: an (almost) full-bleed board with the turn label above
 * it, the rating chip below it, rank numbers to its left, and a
 * keyboard-entry tab to its right — DESIGN.md 4/5절.
 *
 * Deliberately deferred for this first buildable pass (documented, not
 * silently dropped): the piece glyph outline stroke, and animated
 * last-move-square highlighting. Both are cosmetic; the underlying state
 * (selection, correct/wrong/solved feedback, rating) is fully wired.
 */
@Composable
fun PuzzleScreen(viewModel: PuzzleViewModel) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.feedback) {
        if (state.feedback == MoveFeedback.WRONG) {
            delay(600)
            viewModel.clearWrongFeedback()
        }
    }

    val launchMoveInput = rememberMoveInputLauncher { text -> text?.let(viewModel::onSanEntered) }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(maxWidth, maxHeight)
            val boardSide = diameter * Dimens.BoardInsetRatio
            val dimmed = state.isLoading || state.error != null

            TurnLabel(
                state = state,
                onRetry = viewModel::loadNextPuzzle,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 13.dp),
            )

            // The row (ranks + board + keyboard tab) is wider after the board
            // than before it (kbd tab > ranks column), so centering the row
            // as a whole leaves the board itself off-center — and the ranks
            // column, sitting outside the board's own corner (which is
            // already at the circle's edge by construction), clips the round
            // screen on the left. Shift the whole group right by half that
            // width difference to re-center the board and pull the ranks
            // column back inside the safe area.
            val groupShift = (
                Dimens.BoardRowGap + Dimens.KeyboardTabMarginStart + Dimens.KeyboardTabWidth -
                    Dimens.RanksColumnWidth - Dimens.BoardRowGap
                ) / 2

            BoardRow(
                engine = state.engine,
                selected = state.selectedSquare,
                dimmed = dimmed,
                boardSide = boardSide,
                onSquareTapped = viewModel::onSquareTapped,
                onKeyboardTapped = launchMoveInput,
                modifier = Modifier.align(Alignment.Center).offset(x = groupShift),
            )

            RatingChip(
                rating = state.rating,
                delta = state.ratingDelta,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp),
            )

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun TurnLabel(state: PuzzleUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val text: String
    val color: Color
    val clickable: Boolean
    when {
        state.error != null -> {
            text = "Connection Lost"; color = ErrorColor; clickable = true
        }
        state.isLoading -> {
            text = "Loading…"; color = TextSecondary; clickable = false
        }
        state.feedback == MoveFeedback.WRONG -> {
            text = "Try again"; color = ErrorColor; clickable = false
        }
        state.feedback == MoveFeedback.SOLVED -> {
            text = "Correct"; color = Success; clickable = false
        }
        state.engine?.sideToMove == Side.WHITE -> {
            text = "White to move"; color = TextSecondary; clickable = false
        }
        state.engine?.sideToMove == Side.BLACK -> {
            text = "Black to move"; color = TextSecondary; clickable = false
        }
        else -> {
            text = ""; color = TextSecondary; clickable = false
        }
    }
    Text(
        text = text,
        style = AppType.turnLabel,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Bounded to a fraction of the screen width — near the top of a round
        // display the safe chord is much narrower than the full screen width,
        // and an unconstrained Text overflowed into/over the board (caught by
        // an emulator screenshot, not visible from source alone).
        modifier = modifier
            .fillMaxWidth(0.78f)
            .then(if (clickable) Modifier.clickable(onClick = onRetry) else Modifier),
    )
}

@Composable
private fun RatingChip(rating: Int?, delta: Int?, modifier: Modifier = Modifier) {
    if (rating == null) return
    val text = if (delta != null) "$rating (${if (delta >= 0) "+" else ""}$delta)" else "$rating"
    Text(
        text = text,
        style = AppType.ratingChip,
        color = if (delta != null && delta >= 0) Success else TextSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.ChipCornerRadius))
            .background(Surface)
            .padding(horizontal = Dimens.ChipPaddingH, vertical = Dimens.ChipPaddingV),
    )
}

@Composable
private fun BoardRow(
    engine: PuzzleEngine?,
    selected: Square?,
    dimmed: Boolean,
    boardSide: Dp,
    onSquareTapped: (Square) -> Unit,
    onKeyboardTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        RanksColumn(boardSide)
        Spacer(Modifier.width(Dimens.BoardRowGap))
        Board(engine, selected, dimmed, boardSide, onSquareTapped)
        Spacer(Modifier.width(Dimens.BoardRowGap + Dimens.KeyboardTabMarginStart))
        KeyboardTab(boardSide, onKeyboardTapped)
    }
}

@Composable
private fun RanksColumn(boardSide: Dp) {
    Column(modifier = Modifier.size(width = Dimens.RanksColumnWidth, height = boardSide)) {
        for (rank in 8 downTo 1) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = rank.toString(), style = AppType.caption, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun Board(
    engine: PuzzleEngine?,
    selected: Square?,
    dimmed: Boolean,
    boardSide: Dp,
    onSquareTapped: (Square) -> Unit,
) {
    Column(
        modifier = Modifier
            .size(boardSide)
            .then(if (dimmed) Modifier.background(Background.copy(alpha = 0.65f)) else Modifier),
    ) {
        for (row in 0 until 8) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 8) {
                    val square = squareAt(row, col)
                    val isLight = (row + col) % 2 == 0
                    val piece = engine?.board?.getPiece(square) ?: Piece.NONE
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .background(
                                when {
                                    square == selected -> SelectedSquare
                                    isLight -> BoardLight
                                    else -> BoardDark
                                },
                            )
                            .clickable(enabled = !dimmed) { onSquareTapped(square) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (piece != Piece.NONE) {
                            PieceIcon(
                                pieceType = piece.pieceType,
                                isWhite = piece.pieceSide == Side.WHITE,
                                modifier = Modifier.fillMaxSize(0.82f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardTab(boardSide: Dp, onTapped: () -> Unit) {
    val tabHeight = boardSide * Dimens.KeyboardTabHeightRatio
    Box(
        modifier = Modifier
            .size(width = Dimens.KeyboardTabWidth, height = tabHeight)
            .clip(HalfMoonShape)
            .background(Surface)
            .clickable(onClick = onTapped),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "⌨", color = Accent)
    }
}

/**
 * Flat straight edge on the left (flush against the board), fully curved
 * on the right — an elliptical cap (rx = half the shape's own width,
 * ry = half its height), not a circular one.
 *
 * `RoundedCornerShape` can't produce this: each of its corners takes a
 * single circular radius, clamped to fit within *both* adjacent edges — on
 * a tall, narrow box like this one (22dp wide, well over 100dp tall) that
 * clamp caps the radius at roughly half the *width* regardless of what's
 * requested, leaving a long straight run in the middle of the right edge
 * (confirmed on an emulator screenshot, not just reasoned about). A custom
 * outline tracing an explicit half-ellipse is the only way to span the
 * full height with no straight segment left.
 */
private object HalfMoonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val leftCornerRadius = (density.density * 3f).coerceAtMost(minOf(w, h) / 2f)
        val path = Path().apply {
            moveTo(leftCornerRadius, 0f)
            lineTo(w / 2f, 0f)
            // Half-ellipse inscribed in the full w×h box: rx = w/2, ry = h/2 —
            // traced top-center → rightmost point → bottom-center, i.e. the
            // entire right half of the shape becomes one continuous curve.
            arcTo(
                rect = Rect(left = 0f, top = 0f, right = w, bottom = h),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(leftCornerRadius, h)
            arcTo(
                rect = Rect(left = 0f, top = h - 2 * leftCornerRadius, right = 2 * leftCornerRadius, bottom = h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(0f, leftCornerRadius)
            arcTo(
                rect = Rect(left = 0f, top = 0f, right = 2 * leftCornerRadius, bottom = 2 * leftCornerRadius),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            close()
        }
        return Outline.Generic(path)
    }
}

private fun squareAt(row: Int, col: Int): Square {
    val file = ('A' + col)
    val rank = 8 - row
    return Square.valueOf("$file$rank")
}
