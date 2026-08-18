package com.closeonjae.chesspuzzle.opening

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Text
import com.closeonjae.chesspuzzle.R
import com.closeonjae.chesspuzzle.core.lichess.ExplorerMove
import com.closeonjae.chesspuzzle.ui.board.BoardSquare
import com.closeonjae.chesspuzzle.ui.board.HalfMoonShape
import com.closeonjae.chesspuzzle.ui.board.HintTabShape
import com.closeonjae.chesspuzzle.ui.board.SideTab
import com.closeonjae.chesspuzzle.ui.board.isLightSquare
import com.closeonjae.chesspuzzle.ui.board.squareAt
import com.closeonjae.chesspuzzle.ui.theme.AppType
import com.closeonjae.chesspuzzle.ui.theme.Background
import com.closeonjae.chesspuzzle.ui.theme.Dimens
import com.closeonjae.chesspuzzle.ui.theme.ErrorColor
import com.closeonjae.chesspuzzle.ui.theme.OpeningDimens
import com.closeonjae.chesspuzzle.ui.theme.OpeningType
import com.closeonjae.chesspuzzle.ui.theme.Surface
import com.closeonjae.chesspuzzle.ui.theme.TextPrimary
import com.closeonjae.chesspuzzle.ui.theme.TextSecondary
import com.closeonjae.chesspuzzle.ui.theme.WdlBlack
import com.closeonjae.chesspuzzle.ui.theme.WdlDraw
import com.closeonjae.chesspuzzle.ui.theme.WdlFrame
import com.closeonjae.chesspuzzle.ui.theme.WdlWhite
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square

/** How far the previous position's numbers fade while the new position is being looked up (DESIGN.md 9.5절). */
private const val STALE_ALPHA = 0.4f

/**
 * The opening explorer screen: the same frame as the puzzle screen — board
 * centered, a label above it, a chip below it, a half-moon tab on either side
 * — with different things in each slot (DESIGN.md 9절). The hint tab's slot
 * steps the line back; the keyboard tab's slot opens the candidate-move list.
 *
 * Free play, both sides, board fixed to White's perspective: unlike the puzzle
 * screen there is no solver whose side the orientation could follow, and
 * flipping every ply would be the same disorientation the puzzle board's
 * `solverSide` fix was written to avoid.
 */
@Composable
fun OpeningScreen(viewModel: OpeningViewModel) {
    val state by viewModel.uiState.collectAsState()

    // Wear delivers the edge swipe-to-dismiss gesture as a back press, so this
    // one handler covers both the gesture and the hardware back. Registered
    // deeper in the tree than MainActivity's own, so while the list is open it
    // wins and closes just the list; once closed, MainActivity's takes over
    // and returns to the mode picker.
    BackHandler(enabled = state.isSheetOpen, onBack = viewModel::onSheetDismissed)

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(maxWidth, maxHeight)
            val boardSide = diameter * Dimens.BoardInsetRatio

            OpeningNameLabel(
                state = state,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = OpeningDimens.NameTop),
            )

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideTab(
                    boardSide = boardSide,
                    shape = HintTabShape,
                    iconRes = R.drawable.ic_undo,
                    onTapped = viewModel::onUndoTapped,
                    enabled = state.canUndo,
                )
                Spacer(Modifier.width(Dimens.BoardRowGap + Dimens.KeyboardTabMarginStart))
                OpeningBoard(state, boardSide, viewModel::onSquareTapped)
                Spacer(Modifier.width(Dimens.BoardRowGap + Dimens.KeyboardTabMarginStart))
                // A failed lookup turns this tab into retry rather than adding
                // a control — the same "the failed action's own slot offers the
                // retry" rule the puzzle screen follows.
                if (state.isError) {
                    SideTab(
                        boardSide = boardSide,
                        shape = HalfMoonShape,
                        iconRes = R.drawable.ic_retry,
                        onTapped = viewModel::onRetryTapped,
                        tint = ErrorColor,
                    )
                } else {
                    SideTab(
                        boardSide = boardSide,
                        shape = HalfMoonShape,
                        iconRes = R.drawable.ic_move_list,
                        onTapped = viewModel::onListTapped,
                        enabled = state.moves.isNotEmpty(),
                    )
                }
            }

            // Just the win/draw/loss bar now, tucked under the board and as
            // long as the round screen allows there (user request: ECO code and
            // game count removed, bar moved close to the board and lengthened).
            if (state.total > 0) {
                WdlBar(
                    white = state.white,
                    draws = state.draws,
                    black = state.black,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = diameter / 2 + boardSide / 2 + OpeningDimens.WdlBarGap)
                        .alpha(if (state.isStale) STALE_ALPHA else 1f)
                        .width(boardSide * OpeningDimens.WdlBarWidthRatio)
                        .height(OpeningDimens.WdlBarHeight),
                )
            }
        }

        if (state.isSheetOpen) {
            MoveSheet(state, viewModel::onMovePicked, viewModel::onSheetDismissed)
        }
    }
}

/**
 * Opening names read `Family: Variation, Subvariation`, so they split at the
 * first `: ` and take a line each. Both lines are width-capped, and not at the
 * puzzle screen's 0.78: near the top of a round display the usable chord is
 * much narrower than the screen, and narrower still for the upper line — see
 * OpeningDimens.
 */
@Composable
private fun OpeningNameLabel(state: OpeningUiState, modifier: Modifier = Modifier) {
    val family: String
    val variation: String?
    val color: Color
    when {
        state.isError -> {
            family = "Connection Lost"; variation = null; color = ErrorColor
        }
        state.isLoading -> {
            family = "···"; variation = null; color = TextSecondary
        }
        state.opening == null -> {
            // Not an error: a legal position simply nobody has a name for.
            family = "Out of book"; variation = null; color = TextSecondary
        }
        else -> {
            val name = state.opening.name
            val split = name.indexOf(": ")
            family = if (split < 0) name else name.take(split)
            variation = if (split < 0) null else name.substring(split + 2)
            color = TextPrimary
        }
    }

    Column(
        modifier = modifier.alpha(if (state.isStale) STALE_ALPHA else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = family,
            style = OpeningType.openingFamily,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(OpeningDimens.NameWidthLine1),
        )
        if (variation != null) {
            // Shrink once if it doesn't fit, then ellipsize (user choice).
            // Keyed on the text so a new position starts at full size again
            // rather than inheriting the previous name's shrink; a single step
            // down means at most one extra layout pass.
            var style by remember(variation) { mutableStateOf(OpeningType.openingVariation) }
            Text(
                text = variation,
                style = style,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout ->
                    // hasVisualOverflow, not didOverflowWidth: once the line is
                    // ellipsized it *fits* the constraint, so didOverflowWidth
                    // reads false and the shrink never fired (measured — the
                    // long name still truncated at 11sp's ~27 characters).
                    if (layout.hasVisualOverflow && style == OpeningType.openingVariation) {
                        style = OpeningType.openingVariationSmall
                    }
                },
                modifier = Modifier.fillMaxWidth(OpeningDimens.NameWidthLine2),
            )
        }
    }
}

/**
 * Always ordered white wins | draws | black wins, left to right, matching
 * Lichess's own explorer so the reading carries over. The three colors are
 * picked for contrast *against each other* rather than for literal
 * black-and-white, which would disappear against an OLED background — see
 * Color.kt.
 *
 * Drawn rather than laid out as weighted children because a share can be
 * exactly zero, and `weight(0f)` is not allowed.
 *
 * The outline is not decoration: the black share is nearly the background's own
 * color, so without it there is nothing to say where the bar ends and the
 * black-wins segment would read as smaller than it is.
 */
@Composable
private fun WdlBar(white: Int, draws: Int, black: Int, modifier: Modifier = Modifier) {
    val total = (white.toLong() + draws + black).coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f)
        val rounded = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner))
        }
        val whiteWidth = size.width * white / total
        val drawWidth = size.width * draws / total
        clipPath(rounded) {
            drawRect(color = WdlWhite, size = Size(whiteWidth, size.height))
            drawRect(color = WdlDraw, topLeft = Offset(whiteWidth, 0f), size = Size(drawWidth, size.height))
            drawRect(
                color = WdlBlack,
                topLeft = Offset(whiteWidth + drawWidth, 0f),
                size = Size(size.width - whiteWidth - drawWidth, size.height),
            )
        }
        drawRoundRect(color = WdlFrame, cornerRadius = corner, style = Stroke(width = 1.dp.toPx()))
    }
}

/**
 * The same 8×8 grid of [BoardSquare]s the puzzle screen draws, minus the
 * machinery a puzzle needs (move animation, drag-to-move, review stepping) —
 * exploring a line has no wrong move to roll back and no reply to reveal, so a
 * tap either picks a piece up or plays it.
 */
@Composable
private fun OpeningBoard(state: OpeningUiState, boardSide: Dp, onSquareTapped: (Square) -> Unit) {
    val cellSizePx = with(LocalDensity.current) { boardSide.toPx() } / 8f
    // Badges are hidden while a piece is selected: the selection's own legal-move
    // dots are already on the board, and two systems of circles at once
    // stops either being readable (DESIGN.md 9.4절).
    val candidates = if (state.selectedSquare != null) emptyMap() else state.candidates

    Column(
        modifier = Modifier
            .size(boardSide)
            .pointerInput(cellSizePx) {
                detectTapGestures { offset ->
                    val row = (offset.y / cellSizePx).toInt().coerceIn(0, 7)
                    val col = (offset.x / cellSizePx).toInt().coerceIn(0, 7)
                    onSquareTapped(squareAt(row, col))
                }
            },
    ) {
        for (row in 0 until 8) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 8) {
                    val square = squareAt(row, col)
                    val piece = state.pieces.getOrElse(square.ordinal) { Piece.NONE }
                    BoardSquare(
                        piece = piece,
                        isLight = isLightSquare(row, col),
                        isSelected = square == state.selectedSquare,
                        isLastMove = square == state.lastMoveFrom || square == state.lastMoveTo,
                        isHint = false,
                        isCapture = state.legalDestinations[square],
                        showPiece = piece != Piece.NONE,
                        candidateRank = candidates[square],
                    )
                }
            }
        }
    }
}

/**
 * Every candidate move with its share and result split — the comfortable way
 * to read and play them, since a list row is a far bigger target than a square
 * on a 480px watch. Covers the board almost opaquely: half-seeing the board
 * behind the rows helps neither.
 */
@Composable
private fun MoveSheet(state: OpeningUiState, onPick: (ExplorerMove) -> Unit, onDismiss: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = OpeningDimens.SheetScrimAlpha))
            // Tap anywhere off a row to close, alongside the back/swipe
            // gesture. `indication = null`: a ripple across the whole screen
            // would read as if the scrim itself were a control.
            .clickable(interactionSource = interactionSource, indication = null, onClick = onDismiss),
    ) {
        if (state.moves.isEmpty()) {
            Text(
                text = "No games from\nthis position",
                style = AppType.caption,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.7f),
            )
            return@Box
        }

        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            // autoCentering off so the list starts where DESIGN.md 9.3절 puts
            // it — centering the first item would drop the header into the
            // middle of the screen on open.
            autoCentering = null,
            contentPadding = PaddingValues(top = OpeningDimens.MoveListTop, bottom = 40.dp),
        ) {
            item {
                Text(
                    text = "NEXT MOVES",
                    style = AppType.caption.copy(letterSpacing = 1.sp),
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(state.moves) { move -> MoveRow(move, state.total, onPick) }
        }
    }
}

@Composable
private fun MoveRow(move: ExplorerMove, positionTotal: Long, onPick: (ExplorerMove) -> Unit) {
    val share = if (positionTotal > 0) move.total * 100 / positionTotal else 0L
    Column(
        modifier = Modifier
            .fillMaxWidth(OpeningDimens.MoveListWidth)
            .clip(RoundedCornerShape(Dimens.ChipCornerRadius))
            .background(Surface)
            .clickable { onPick(move) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = move.san, style = OpeningType.moveSan, color = TextPrimary)
            Text(text = "$share%", style = AppType.ratingChip, color = TextSecondary)
        }
        WdlBar(
            white = move.white,
            draws = move.draws,
            black = move.black,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(OpeningDimens.WdlBarHeight),
        )
        // Only the moves that actually rename the opening carry this line —
        // the explorer fills it exactly at those branch points, so most rows
        // stay two lines tall.
        move.opening?.let { opening ->
            Text(
                text = opening.name,
                style = OpeningType.moveOpening,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
