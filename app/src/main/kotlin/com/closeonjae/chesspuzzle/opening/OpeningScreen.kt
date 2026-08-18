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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
import com.closeonjae.chesspuzzle.ui.board.rowColOf
import com.closeonjae.chesspuzzle.ui.board.squareAt
import com.closeonjae.chesspuzzle.ui.theme.AppType
import com.closeonjae.chesspuzzle.ui.theme.ArrowTint
import com.closeonjae.chesspuzzle.ui.theme.Background
import com.closeonjae.chesspuzzle.ui.theme.CandidateMarker
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
        AutoShrinkLine(
            text = family,
            base = OpeningType.openingFamily,
            small = OpeningType.openingFamilySmall,
            color = color,
            widthFraction = OpeningDimens.NameWidthLine1,
        )
        if (variation != null) {
            AutoShrinkLine(
                text = variation,
                base = OpeningType.openingVariation,
                small = OpeningType.openingVariationSmall,
                color = TextSecondary,
                widthFraction = OpeningDimens.NameWidthLine2,
            )
        }
    }
}

/**
 * One line of the opening name: full size if it fits, one step down if it
 * doesn't, ellipsized only if it still doesn't (user choice).
 *
 * Both lines need this, not just the variation — line 1 sits *higher* on the
 * round screen, where the chord is narrower still, and "King's Knight Opening"
 * was already too long for it at full size (seen on the real watch).
 *
 * Keyed on the text so a new position starts at full size again instead of
 * inheriting the previous name's shrink, and the step is a single one, so this
 * costs at most one extra layout pass.
 */
@Composable
private fun AutoShrinkLine(
    text: String,
    base: TextStyle,
    small: TextStyle,
    color: Color,
    widthFraction: Float,
) {
    var style by remember(text, base) { mutableStateOf(base) }
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        // hasVisualOverflow, not didOverflowWidth: once a line is ellipsized it
        // *fits* the constraint, so didOverflowWidth reads false and the shrink
        // never fires — measured, the long name stayed cut off at 11sp.
        onTextLayout = { layout -> if (layout.hasVisualOverflow && style == base) style = small },
        modifier = Modifier.fillMaxWidth(widthFraction),
    )
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
    // Badges and arrows are hidden while a piece is selected: the selection's
    // own legal-move dots are already on the board, and two systems of marks at
    // once stops either being readable (DESIGN.md 9.4절).
    val candidates = if (state.selectedSquare != null) emptyList() else state.candidates
    val ranks = remember(candidates) { candidates.associate { it.to to it.rank } }

    Box(
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
        Column(modifier = Modifier.fillMaxSize()) {
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
                            candidateRank = ranks[square],
                        )
                    }
                }
            }
        }
        CandidateArrows(candidates)
    }
}

/**
 * An arrow per candidate move, drawn over the whole board rather than per
 * square (user request) — a badge marks where a move *lands*, which does not
 * say which piece goes there. Two knights can reach the same square, and a
 * badge on f3 means nothing on a board until you can see it came from g1.
 *
 * Straight lines, including for knights: an L-shaped path is more literal but
 * needs three times the ink on a board whose squares are about 21dp, and the
 * pair of endpoints identifies the move either way.
 *
 * Centre to centre, thick, and translucent red (user request). The three go
 * together: running the full square-to-square span puts the tail on top of the
 * piece being pointed out, so the tint has to be seen *through* rather than
 * cover it, and the extra weight is what keeps a 50%-alpha line legible over
 * both square colours and the pieces.
 */
@Composable
private fun CandidateArrows(candidates: List<CandidateMove>) {
    if (candidates.isEmpty()) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cell = size.width / 8f
        val headLength = cell * OpeningDimens.ArrowHeadLengthRatio
        val headHalfWidth = cell * OpeningDimens.ArrowHeadWidthRatio / 2f
        val badgeClearance = cell * (OpeningDimens.CandidateMarkerRatio / 2f + OpeningDimens.ArrowBadgeGapRatio)
        candidates.forEach { candidate ->
            val start = centerOf(candidate.from, cell)
            val end = centerOf(candidate.to, cell)
            val span = end - start
            val length = span.getDistance()
            if (length < 1f) return@forEach
            val dir = span / length
            // Centre to centre at both ends (user request) — no inset at the
            // tail, no gap before the badge at the tip.
            val tail = start
            val tip = end
            if (length < headLength) return@forEach
            val base = tip - dir * headLength
            // Draw the shaft in pieces, leaving a gap wherever it would run
            // through *another* candidate's badge. Two moves from the same
            // square along the same line (a pawn's one- and two-square push)
            // are common, and without this the longer arrow strikes straight
            // through the nearer badge's digit.
            val shaftLength = (base - tail).getDistance()
            val blocked = candidates
                .filter { it.rank != candidate.rank }
                .mapNotNull { other ->
                    val toOther = centerOf(other.to, cell) - tail
                    val along = toOther.x * dir.x + toOther.y * dir.y
                    val across = (toOther - dir * along).getDistance()
                    if (across >= badgeClearance) {
                        null
                    } else {
                        val half = sqrt(badgeClearance * badgeClearance - across * across)
                        (along - half) to (along + half)
                    }
                }
                .sortedBy { it.first }
            var cursor = 0f
            for ((blockStart, blockEnd) in blocked) {
                if (blockStart > cursor) {
                    drawArrowShaft(tail, dir, cursor, min(blockStart, shaftLength), cell)
                }
                cursor = max(cursor, blockEnd)
                if (cursor >= shaftLength) break
            }
            if (cursor < shaftLength) drawArrowShaft(tail, dir, cursor, shaftLength, cell)
            val perpendicular = Offset(-dir.y, dir.x)
            drawPath(
                path = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(base.x + perpendicular.x * headHalfWidth, base.y + perpendicular.y * headHalfWidth)
                    lineTo(base.x - perpendicular.x * headHalfWidth, base.y - perpendicular.y * headHalfWidth)
                    close()
                },
                color = ArrowTint,
            )
        }
    }
}

/** One run of an arrow's shaft, between two distances along [dir] from [tail]. */
private fun DrawScope.drawArrowShaft(tail: Offset, dir: Offset, from: Float, to: Float, cell: Float) {
    // Anything shorter than the stroke is a dot, not a line — skip it rather
    // than leave a speck floating between two badges.
    val stroke = cell * OpeningDimens.ArrowStrokeRatio
    if (to - from <= stroke) return
    drawLine(
        color = ArrowTint,
        start = tail + dir * from,
        end = tail + dir * to,
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

/** Centre of [square] in board pixels. White is always at the bottom here, so there is no flip to apply. */
private fun centerOf(square: Square, cell: Float): Offset {
    val (row, col) = rowColOf(square)
    return Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
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
