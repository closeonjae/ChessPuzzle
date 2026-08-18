package com.closeonjae.chesspuzzle.ui.board

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.closeonjae.chesspuzzle.ui.theme.CandidateMarker
import com.closeonjae.chesspuzzle.ui.theme.OpeningDimens
import com.closeonjae.chesspuzzle.ui.theme.OpeningType
import com.closeonjae.chesspuzzle.ui.theme.BoardDark
import com.closeonjae.chesspuzzle.ui.theme.BoardLight
import com.closeonjae.chesspuzzle.ui.theme.HintTint
import com.closeonjae.chesspuzzle.ui.theme.LastMoveTint
import com.closeonjae.chesspuzzle.ui.theme.LegalDot
import com.closeonjae.chesspuzzle.ui.theme.SelectedSquare
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side

/**
 * One of a board's 64 cells, extracted out of the boards' own loops purely so
 * Compose can *skip* it. Shared by the puzzle and the opening screens so the
 * two boards are, pixel for pixel, the same board (user request: 퍼즐에서 만든
 * 디자인 최대한 따라서).
 *
 * Measured from the Compose compiler's own stability report (`app-composables.txt`,
 * `composeCompiler { reportsDestination = ... }`): the puzzle's `Board` takes
 * `PuzzleEngine` and three `Move?`s, all of which the compiler marks
 * **unstable** — chesslib's `Move` is a mutable Java class and `PuzzleEngine`
 * wraps a mutable `Board`, and neither is ours to annotate. An unstable
 * parameter means that composable can never be skipped, so it re-ran on
 * *every* `PuzzleUiState` emission (select a piece, play a move, reveal the
 * opponent's reply, a rating update, a background next-puzzle fetch landing…).
 * With all 64 cells inlined into that one restart scope, each of those
 * emissions rebuilt 64 modifier chains and re-composed up to 32 `PieceIcon`s
 * (each doing its own `painterResource` resource-table lookup) — even when a
 * single square had actually changed.
 *
 * Every parameter here is deliberately a stable value type (enum / Boolean /
 * `Boolean?` / `Int?`) computed by the caller, and there are no lambda
 * parameters (taps are handled by the parent's single `pointerInput`), so the
 * compiler marks this one `skippable` and equal arguments skip outright. The
 * caller's scope still re-runs — 64 O(1) array lookups and comparisons — but
 * only the squares whose appearance genuinely changed recompose.
 *
 * The `RowScope` receiver (rather than a `modifier` parameter) keeps
 * `weight(1f)` inside the skipped body and keeps the caller from allocating a
 * fresh `Modifier` per cell per pass; `RowScope` is `@Immutable`, so it
 * doesn't cost the skip.
 *
 * @param candidateRank popularity rank (1 = most played) of an opening-explorer
 *   candidate move that lands here, or null for none. Always null on the puzzle
 *   screen.
 */
@Composable
fun RowScope.BoardSquare(
    piece: Piece,
    isLight: Boolean,
    isSelected: Boolean,
    isLastMove: Boolean,
    isHint: Boolean,
    isCapture: Boolean?,
    showPiece: Boolean,
    candidateRank: Int? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .background(
                when {
                    isSelected -> SelectedSquare
                    isLight -> BoardLight
                    else -> BoardDark
                },
            )
            .then(
                // Last-moved from/to squares (user request): same
                // translucent-wash treatment as the hint square below,
                // drawn first so the hint tint wins if a square is
                // somehow both at once.
                if (isLastMove) Modifier.background(LastMoveTint) else Modifier,
            )
            .then(
                // Hint square (user request): not a full selection look
                // — just a translucent color wash sitting between the
                // square's own background and the piece drawn on top of
                // it (a second .background() layers over the first but
                // still stays behind the piece, which is this Box's
                // actual child content).
                if (isHint) Modifier.background(HintTint) else Modifier,
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
        if (showPiece) {
            PieceIcon(
                pieceType = piece.pieceType,
                isWhite = piece.pieceSide == Side.WHITE,
                modifier = Modifier.fillMaxSize(0.82f),
            )
        }
        if (candidateRank != null) {
            CandidateBadge(candidateRank)
        }
    }
}

/**
 * The popularity badge (user request: 인기 수는 원 안에 있는 숫자로) — a ring and a
 * rank digit, nothing filled behind them (user request: 배경 없애고 검은색으로 원
 * 테두리랑 숫자만, 투명도 50%). Because it is see-through, a candidate that
 * captures needs no separate marking: the piece it would take stays visible.
 *
 * A single rank digit rather than a percentage: a square is ~21dp on the watch,
 * so the circle only holds one legible digit — the exact share is one tap away
 * in the move list.
 *
 * Ring and digit are drawn in **one pass off the same float centre**, which is
 * what actually keeps the digit centred (user report: 숫자가 오른쪽으로 치우쳐
 * 보임). Two earlier attempts missed the real cause, both confirmed by
 * measuring an emulator screenshot:
 *
 *  1. Zeroing letter spacing, forcing tabular figures and centring the
 *     paragraph across the full width changed nothing — none of them move the
 *     ink inside the glyph's advance.
 *  2. Centring the digit on its ink with `getTextBounds`, but as a child of a
 *     `Box` that drew the ring with `Modifier.border`, still left up to ~1px:
 *     the board's 8 columns don't divide evenly, so squares differ by a pixel,
 *     and the ring's box and the digit's box were rounded *independently*.
 *
 * Drawing both from `DrawScope.center` removes that intermediate rounding
 * entirely, and `getTextBounds` puts the ink — not the advance box, which a
 * digit does not sit centred in — on that same point.
 */
/**
 * Extra leftward nudge on the badge digit, as a fraction of its text size
 * (user request, twice: 숫자가 아직 오른쪽으로 치우쳐 보인다).
 *
 * This is an *optical* adjustment, not a bug fix — the geometry underneath is
 * already centred, first on the int ink box and then on the glyph outline's
 * float bounds, and screenshots confirm both put the ink's midpoint on the
 * ring's fitted centre. Digits simply don't read as centred when their ink box
 * is: the weight of 1, 4 and 5 sits to the right of their own bounding box.
 * Expressed against the text size so it scales with the badge rather than
 * being a pixel constant that only holds at one density.
 */
private const val DIGIT_OPTICAL_SHIFT = 0.05f

@Composable
private fun BoxScope.CandidateBadge(rank: Int) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { OpeningType.markerDigit.fontSize.toPx() }
    val strokePx = with(density) { OpeningDimens.MarkerBorderWidth.toPx() }
    // The paint and the digit's ink bounds only depend on the text size and the
    // digit, so neither is rebuilt on the board updates that redraw this.
    val paint = remember(fontSizePx) {
        Paint().apply {
            isAntiAlias = true
            color = CandidateMarker.toArgb()
            textSize = fontSizePx
            typeface = Typeface.create(Typeface.DEFAULT, FontWeight.SemiBold.weight, false)
        }
    }
    val text = rank.toString()
    // Float ink bounds off the glyph outline, not `Paint.getTextBounds` — that
    // one returns an **int** `Rect`, and its half-pixel quantisation is a
    // visible slice of a badge only ~14dp across (user report: still leaning
    // right after the int-bounds version).
    val inkBounds = remember(text, paint) {
        RectF().also { bounds ->
            AndroidPath().let { outline ->
                paint.getTextPath(text, 0, text.length, 0f, 0f, outline)
                outline.computeBounds(bounds, true)
            }
        }
    }

    Canvas(modifier = Modifier.matchParentSize()) {
        val radius = size.minDimension * OpeningDimens.CandidateMarkerRatio / 2f
        drawCircle(
            color = CandidateMarker,
            radius = radius - strokePx / 2f,
            style = Stroke(width = strokePx),
        )
        drawContext.canvas.nativeCanvas.drawText(
            text,
            center.x - inkBounds.centerX() - fontSizePx * DIGIT_OPTICAL_SHIFT,
            center.y - inkBounds.centerY(),
            paint,
        )
    }
}
