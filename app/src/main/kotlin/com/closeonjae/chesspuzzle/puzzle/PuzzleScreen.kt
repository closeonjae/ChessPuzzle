package com.closeonjae.chesspuzzle.puzzle

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Text
import com.closeonjae.chesspuzzle.R
import com.closeonjae.chesspuzzle.core.puzzle.PuzzleEngine
import com.closeonjae.chesspuzzle.input.rememberMoveInputLauncher
import com.closeonjae.chesspuzzle.ui.theme.Accent
import com.closeonjae.chesspuzzle.ui.theme.AppType
import com.closeonjae.chesspuzzle.ui.theme.Background
import com.closeonjae.chesspuzzle.ui.theme.BoardDark
import com.closeonjae.chesspuzzle.ui.theme.BoardLight
import com.closeonjae.chesspuzzle.ui.theme.Dimens
import com.closeonjae.chesspuzzle.ui.theme.ErrorColor
import com.closeonjae.chesspuzzle.ui.theme.HintTint
import com.closeonjae.chesspuzzle.ui.theme.LastMoveTint
import com.closeonjae.chesspuzzle.ui.theme.LegalDot
import com.closeonjae.chesspuzzle.ui.theme.RatingDown
import com.closeonjae.chesspuzzle.ui.theme.RatingUp
import com.closeonjae.chesspuzzle.ui.theme.SelectedSquare
import com.closeonjae.chesspuzzle.ui.theme.Success
import com.closeonjae.chesspuzzle.ui.theme.Surface
import com.closeonjae.chesspuzzle.ui.theme.TextSecondary
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The puzzle screen: an (almost) full-bleed board with the turn label above
 * it, the rating chip below it, a hint tab to its left, and a
 * keyboard-entry tab to its right — DESIGN.md 4/5절. The board itself
 * flips to the solver's own perspective when it's Black to move (own
 * pieces always nearest the bottom of the screen), same as most chess UIs.
 *
 * Deliberately deferred for this first buildable pass (documented, not
 * silently dropped): animated last-move-square highlighting. Cosmetic only
 * — the underlying state (selection, correct/wrong/solved feedback,
 * rating) is fully wired.
 */
@Composable
fun PuzzleScreen(viewModel: PuzzleViewModel) {
    val state by viewModel.uiState.collectAsState()

    val launchMoveInput = rememberMoveInputLauncher { text -> text?.let(viewModel::onSanEntered) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .then(
                // Wrong move (user request): no button, just tap anywhere on
                // screen to restore the board — the wrong move itself was
                // already undone underneath, this just dismisses the dim/
                // "Retry" state. Only active while that state is showing, so
                // it doesn't steal normal board taps otherwise.
                if (state.feedback == MoveFeedback.WRONG) {
                    Modifier.clickable(onClick = viewModel::clearWrongFeedback)
                } else {
                    Modifier
                },
            ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val diameter = minOf(maxWidth, maxHeight)
            val boardSide = diameter * Dimens.BoardInsetRatio
            // A wrong move now dims the board and waits for an explicit Retry
            // tap instead of auto-clearing after a timer (user request) — the
            // wrong move is already undone underneath, so tapping Retry is
            // exactly "go back to where I was, try again".
            val dimmed = state.isLoading || state.error != null || state.feedback == MoveFeedback.WRONG

            TurnLabel(
                state = state,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 13.dp),
            )

            // Hint tab (left) and keyboard tab (right) are now the same
            // size with the same gap on either side of the board, so the
            // row is symmetric and centers on the board with no extra
            // offset needed (previously the ranks column was narrower than
            // the keyboard tab and the whole row had to be nudged right).
            BoardRow(
                engine = state.engine,
                selected = state.selectedSquare,
                hintSquare = state.hintSquare,
                dimmed = dimmed,
                boardSide = boardSide,
                onSquareTapped = viewModel::onSquareTapped,
                onKeyboardTapped = launchMoveInput,
                onHintTapped = viewModel::onHintTapped,
                modifier = Modifier.align(Alignment.Center),
            )

            RatingChip(
                rating = state.rating,
                delta = state.ratingDelta,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 11.dp),
            )

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Centered retry button (user request) — the top "Connection
            // Lost" text used to double as the only retry affordance; a
            // real button in the middle of the screen is a much more
            // obvious target than small text at the very top edge. Wear
            // Compose Material3's regular Button enforces its own minimum
            // height regardless of an explicit smaller Modifier.height, so
            // "shorter still" (user request, twice) needed the dedicated
            // CompactButton variant instead — its own default height/
            // padding are built to be shorter, rather than fighting the
            // standard Button's floor.
            // A wrong move deliberately does *not* get this button (user
            // request) — just the top "Retry" text plus the tap-anywhere
            // handler on the root Box above.
            if (state.error != null) {
                CompactButton(
                    onClick = viewModel::loadNextPuzzle,
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(Dimens.ButtonCornerRadius),
                ) {
                    Text(text = "Retry", style = AppType.buttonLabel, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun TurnLabel(state: PuzzleUiState, modifier: Modifier = Modifier) {
    val text: String
    val color: Color
    when {
        state.error != null -> {
            text = "Connection Lost"; color = ErrorColor
        }
        state.isLoading -> {
            text = "Loading…"; color = TextSecondary
        }
        state.feedback == MoveFeedback.WRONG -> {
            text = "Retry"; color = ErrorColor
        }
        state.feedback == MoveFeedback.SOLVED -> {
            text = "Correct"; color = Success
        }
        state.engine?.sideToMove == Side.WHITE -> {
            text = "White to move"; color = TextSecondary
        }
        state.engine?.sideToMove == Side.BLACK -> {
            text = "Black to move"; color = TextSecondary
        }
        else -> {
            text = ""; color = TextSecondary
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
        modifier = modifier.fillMaxWidth(0.78f),
    )
}

@Composable
private fun RatingChip(rating: Int?, delta: Int?, modifier: Modifier = Modifier) {
    if (rating == null) return
    // No parens around the delta; only the delta digits get the up/down
    // color (Korean market convention: red = up, blue = down) — the rating
    // number itself stays textSecondary.
    val text = buildAnnotatedString {
        append(rating.toString())
        if (delta != null) {
            append(" ")
            withStyle(SpanStyle(color = if (delta >= 0) RatingUp else RatingDown)) {
                append(if (delta >= 0) "+$delta" else "$delta")
            }
        }
    }
    Text(
        text = text,
        style = AppType.ratingChip,
        color = TextSecondary,
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
    hintSquare: Square?,
    dimmed: Boolean,
    boardSide: Dp,
    onSquareTapped: (Square) -> Unit,
    onKeyboardTapped: () -> Unit,
    onHintTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        HintTab(boardSide, onHintTapped)
        Spacer(Modifier.width(Dimens.BoardRowGap + Dimens.KeyboardTabMarginStart))
        Board(engine, selected, hintSquare, dimmed, boardSide, onSquareTapped)
        Spacer(Modifier.width(Dimens.BoardRowGap + Dimens.KeyboardTabMarginStart))
        KeyboardTab(boardSide, onKeyboardTapped)
    }
}

/** Magnification while long-pressed (user request) — big enough to meaningfully
 * help precision-tap the small squares, small enough that panning still shows
 * useful context around the target. */
private const val ZOOM_SCALE = 2.2f

/** Extra multiplier on top of the "physically accurate" finger-delta/ZOOM_SCALE
 * pan — user request ("감도를 더 높여줘"): the 1:1-feeling pan was too sluggish
 * for reaching far corners of the board, so panning now outruns the finger.
 * Later dialed back down a step ("감도 조금 줄여줘") once 3f felt overshooty. */
private const val ZOOM_PAN_SENSITIVITY = 2f

@Composable
private fun Board(
    engine: PuzzleEngine?,
    selected: Square?,
    hintSquare: Square?,
    dimmed: Boolean,
    boardSide: Dp,
    onSquareTapped: (Square) -> Unit,
) {
    // Flip to the solver's own perspective when it's Black to move (own
    // pieces nearest the bottom) — user request. Only the *display*
    // position is mirrored: which true row/col is drawn at each grid
    // cell. isLight below stays keyed off the true (unflipped) row/col,
    // since a square's own color never changes with viewing angle.
    val flipped = engine?.sideToMove == Side.BLACK
    // Legal destinations of the selected piece, keyed to whether they
    // capture (an opponent piece is there) or not — user request: an empty
    // legal square gets a small dot, a capturable square gets a ring
    // inscribed in the whole square, both in the same legalDot color. The
    // hinted square deliberately does *not* get this treatment (user
    // request — it should read as a hint, not as if the solver tapped it
    // themselves); see the separate hintTint overlay below instead.
    val legalDestinations: Map<Square, Boolean> = if (selected != null && engine != null) {
        engine.board.legalMoves()
            .filter { it.from == selected }
            .associate { it.to to (engine.board.getPiece(it.to) != Piece.NONE) }
    } else {
        emptyMap()
    }
    // Last-moved from/to squares (user request) — reused the color that
    // used to be hintTint's, once hint moved to red (see Color.kt).
    val lastMove = engine?.lastMove

    val density = LocalDensity.current
    val boardSidePx = with(density) { boardSide.toPx() }
    val cellSizePx = boardSidePx / 8f

    fun squareFromOffset(offset: Offset): Square {
        val row = (offset.y / cellSizePx).toInt().coerceIn(0, 7)
        val col = (offset.x / cellSizePx).toInt().coerceIn(0, 7)
        return squareAt(if (flipped) 7 - row else row, if (flipped) 7 - col else col)
    }
    fun isOwnPieceAt(square: Square): Boolean {
        val piece = engine?.board?.getPiece(square) ?: return false
        return piece != Piece.NONE && piece.pieceSide == engine.sideToMove
    }

    // Drag-to-move state: the piece being dragged (if any) is skipped in its
    // origin cell below and drawn instead as a floating overlay that tracks
    // the raw finger offset from where the drag started.
    var dragOriginSquare by remember { mutableStateOf<Square?>(null) }
    var dragOffsetPx by remember { mutableStateOf(Offset.Zero) }
    // Long-press-to-zoom state (user request): zoomFocusPx is the
    // board-local point currently centered/magnified — it starts at the
    // press point and pans by the finger's screen movement scaled down by
    // ZOOM_SCALE (so the content really does track under the finger, the
    // same way a magnifying glass does). Releasing acts as a tap on
    // whatever square is at the focus, i.e. under the center reticle.
    var zoomFocusPx by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = Modifier.size(boardSide).clipToBounds()) {
        Column(
            modifier = Modifier
                .size(boardSide)
                .then(if (dimmed) Modifier.background(Background.copy(alpha = 0.65f)) else Modifier)
                .then(
                    zoomFocusPx?.let { focus ->
                        Modifier.graphicsLayer(
                            scaleX = ZOOM_SCALE,
                            scaleY = ZOOM_SCALE,
                            translationX = boardSidePx / 2f - focus.x * ZOOM_SCALE,
                            translationY = boardSidePx / 2f - focus.y * ZOOM_SCALE,
                            transformOrigin = TransformOrigin(0f, 0f),
                        )
                    } ?: Modifier,
                )
                .then(
                    if (dimmed) {
                        Modifier
                    } else {
                        Modifier.pointerInput(engine, flipped) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downSquare = squareFromOffset(down.position)
                                val slop = viewConfiguration.touchSlop
                                // Race the first moment any of these happens: pointer moves
                                // past touch slop (drag — checked first: a synthetic/fast
                                // swipe can deliver its move and its lift in the very same
                                // event, so "did it move" must win over "did it lift" or a
                                // real drag gets misread as a plain tap), pointer lifts
                                // without much movement (plain tap), or neither happens
                                // before the long-press timeout (zoom). raceEndChange carries
                                // the event that decided "drag" onward, since it may *also*
                                // already be the release.
                                var raceEndChange: PointerInputChange? = null
                                val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                        if ((change.position - down.position).getDistance() > slop) {
                                            raceEndChange = change
                                            return@withTimeoutOrNull "drag"
                                        }
                                        if (change.changedToUpIgnoreConsumed()) {
                                            change.consume()
                                            return@withTimeoutOrNull "tap"
                                        }
                                    }
                                    @Suppress("UNREACHABLE_CODE") null
                                }
                                when (outcome) {
                                    "tap" -> onSquareTapped(downSquare)
                                    "drag" -> if (isOwnPieceAt(downSquare)) {
                                        dragOriginSquare = downSquare
                                        onSquareTapped(downSquare) // select it, same as a first tap
                                        var change = raceEndChange
                                        while (change != null) {
                                            dragOffsetPx = change.position - down.position
                                            val consumedChange = change
                                            consumedChange.consume()
                                            if (consumedChange.changedToUpIgnoreConsumed()) {
                                                onSquareTapped(squareFromOffset(consumedChange.position))
                                                break
                                            }
                                            val event = awaitPointerEvent()
                                            change = event.changes.firstOrNull { it.id == down.id }
                                        }
                                        dragOriginSquare = null
                                        dragOffsetPx = Offset.Zero
                                    } else if (raceEndChange?.changedToUpIgnoreConsumed() != true) {
                                        // Not a piece of ours to pick up — drain the rest of
                                        // the gesture as a no-op rather than let it leak into
                                        // the next one.
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            change.consume()
                                            if (change.changedToUpIgnoreConsumed()) break
                                        }
                                    }
                                    else -> { // long-press timeout: zoom + pan
                                        var focus = down.position
                                        var lastPos = down.position
                                        zoomFocusPx = focus
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            change.consume()
                                            if (change.changedToUpIgnoreConsumed()) {
                                                onSquareTapped(squareFromOffset(focus))
                                                break
                                            }
                                            focus += (change.position - lastPos) * ZOOM_PAN_SENSITIVITY / ZOOM_SCALE
                                            focus = Offset(
                                                focus.x.coerceIn(0f, boardSidePx),
                                                focus.y.coerceIn(0f, boardSidePx),
                                            )
                                            lastPos = change.position
                                            zoomFocusPx = focus
                                        }
                                        zoomFocusPx = null
                                    }
                                }
                            }
                        }
                    },
                ),
        ) {
            for (displayRow in 0 until 8) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (displayCol in 0 until 8) {
                        val row = if (flipped) 7 - displayRow else displayRow
                        val col = if (flipped) 7 - displayCol else displayCol
                        val square = squareAt(row, col)
                        val isLight = (row + col) % 2 == 0
                        val piece = engine?.board?.getPiece(square) ?: Piece.NONE
                        val isCapture = legalDestinations[square]
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
                                .then(
                                    // Last-moved from/to squares (user request): same
                                    // translucent-wash treatment as the hint square below,
                                    // drawn first so the hint tint wins if a square is
                                    // somehow both at once.
                                    if (square == lastMove?.from || square == lastMove?.to) {
                                        Modifier.background(LastMoveTint)
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    // Hint square (user request): not a full selection look
                                    // — just a translucent color wash sitting between the
                                    // square's own background and the piece drawn on top of
                                    // it (a second .background() layers over the first but
                                    // still stays behind the piece, which is this Box's
                                    // actual child content).
                                    if (square == hintSquare) Modifier.background(HintTint) else Modifier,
                                )
                                .then(
                                    when (isCapture) {
                                        // Hollow ring inscribed in the square, stroke width
                                        // 2/3 of the small dot's own radius (user request) —
                                        // its outer edge stays where the old filled circle
                                        // was, so the footprint is unchanged, just hollowed out.
                                        true -> Modifier.drawWithContent {
                                            val dotRadius = size.minDimension * 0.15f
                                            val ringWidth = dotRadius * 2f / 3f
                                            drawCircle(
                                                color = LegalDot,
                                                radius = size.minDimension / 2f - ringWidth / 2f,
                                                style = Stroke(width = ringWidth),
                                            )
                                            drawContent()
                                        }
                                        false -> Modifier.drawWithContent {
                                            drawCircle(color = LegalDot, radius = size.minDimension * 0.15f)
                                            drawContent()
                                        }
                                        null -> Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (piece != Piece.NONE && square != dragOriginSquare) {
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

        // Floating piece dragged out of its origin square, following the
        // raw (unzoomed) finger offset — drawn as a sibling above the
        // (possibly zoomed) board, so it's never itself scaled/panned.
        dragOriginSquare?.let { origin ->
            val piece = engine?.board?.getPiece(origin) ?: Piece.NONE
            if (piece != Piece.NONE) {
                val (row, col) = rowColOf(origin)
                val displayRow = if (flipped) 7 - row else row
                val displayCol = if (flipped) 7 - col else col
                val centerPx = Offset((displayCol + 0.5f) * cellSizePx, (displayRow + 0.5f) * cellSizePx)
                val pieceSizePx = cellSizePx * 0.82f
                val topLeftPx = centerPx + dragOffsetPx - Offset(pieceSizePx / 2f, pieceSizePx / 2f)
                PieceIcon(
                    pieceType = piece.pieceType,
                    isWhite = piece.pieceSide == Side.WHITE,
                    modifier = Modifier
                        .size(with(density) { pieceSizePx.toDp() })
                        .offset { IntOffset(topLeftPx.x.roundToInt(), topLeftPx.y.roundToInt()) },
                )
            }
        }

    }
}

private fun rowColOf(square: Square): Pair<Int, Int> {
    val name = square.toString()
    val col = name[0] - 'A'
    val row = 8 - (name[1] - '0')
    return row to col
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
        Text(text = "⌨", style = AppType.caption, color = Accent)
    }
}

/**
 * Same size/style as [KeyboardTab] (user request: "동일한 양식") — mirrored to
 * sit on the board's *left*, so its curve faces the screen's left edge
 * instead of the right. A first tap reveals which square's piece the
 * solver needs to move ([hintSquare] outline in [Board]); a second tap
 * while that's showing plays the move.
 */
@Composable
private fun HintTab(boardSide: Dp, onTapped: () -> Unit) {
    val tabHeight = boardSide * Dimens.KeyboardTabHeightRatio
    Box(
        modifier = Modifier
            .size(width = Dimens.KeyboardTabWidth, height = tabHeight)
            .clip(HintTabShape)
            .background(Surface)
            .clickable(onClick = onTapped),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_hint),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Accent),
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Small rounded corners on the left (flush against the board), a much
 * bigger elliptical curve on the right — matching the design mockup's
 * `.kbd-tab` CSS exactly (DESIGN.md 산출물 HTML): `border-radius: 3px 19px
 * 19px 3px / 3px 46px 46px 3px` on a 22×92 box. The two right corners'
 * radii (19 horizontal, 46 vertical) aren't independent of the left ones —
 * 3+19 = 22 (the box's own width) and 46+46 = 92 (its own height), so the
 * curve starts the instant the small left corner ends, with zero flat run
 * left over anywhere on the outline.
 *
 * `RoundedCornerShape` can't produce this: each of its corners takes a
 * single circular radius, clamped to fit within *both* adjacent edges — on
 * a tall, narrow box like this one that clamp caps the radius at roughly
 * half the *width* regardless of what's requested, leaving a long straight
 * run in the middle of the right edge (confirmed on an emulator
 * screenshot, not just reasoned about). A custom outline tracing an
 * explicit ellipse is the only way to reproduce the CSS shape.
 */
private object HalfMoonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val leftCornerRadius = (density.density * 3f).coerceAtMost(minOf(w, h) / 2f)
        // Right-side ellipse: rx = w - leftCornerRadius (reaches the full
        // right edge, same as the CSS corner radii summing to the box
        // width), ry = h/2 so its top and bottom quarters meet exactly at
        // the vertical midpoint.
        val rightRx = (w - leftCornerRadius).coerceAtLeast(0f)
        val path = Path().apply {
            moveTo(leftCornerRadius, 0f)
            // Traced top-center → rightmost point → bottom-center as one
            // continuous curve, starting exactly where the moveTo left off
            // (no straight segment in between).
            arcTo(
                rect = Rect(left = w - 2 * rightRx, top = 0f, right = w, bottom = h),
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

/**
 * The exact horizontal mirror of [HalfMoonShape] — small corners on the
 * right (flush against the board), the big curve on the left (facing the
 * screen's edge). Every coordinate is [HalfMoonShape]'s own reflected
 * across x → w−x; each `arcTo`'s start angle and rect follow the same
 * reflection, and its sweep is negated (mirroring reverses the arc's
 * winding direction) — verified by hand that each segment's start/end
 * point lands exactly on the previous/next segment's, same as the
 * original.
 */
private object HintTabShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val cornerRadius = (density.density * 3f).coerceAtMost(minOf(w, h) / 2f)
        val bigRx = (w - cornerRadius).coerceAtLeast(0f)
        val path = Path().apply {
            moveTo(w - cornerRadius, 0f)
            arcTo(
                rect = Rect(left = 0f, top = 0f, right = 2 * bigRx, bottom = h),
                startAngleDegrees = -90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false,
            )
            lineTo(w - cornerRadius, h)
            arcTo(
                rect = Rect(left = w - 2 * cornerRadius, top = h - 2 * cornerRadius, right = w, bottom = h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(w, cornerRadius)
            arcTo(
                rect = Rect(left = w - 2 * cornerRadius, top = 0f, right = w, bottom = 2 * cornerRadius),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -90f,
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
