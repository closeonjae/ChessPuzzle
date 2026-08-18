package com.closeonjae.chesspuzzle.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.wear.compose.material3.Text
import com.closeonjae.chesspuzzle.ui.theme.Accent
import com.closeonjae.chesspuzzle.ui.theme.OpeningDimens
import com.closeonjae.chesspuzzle.ui.theme.OpeningType
import com.closeonjae.chesspuzzle.ui.theme.BoardDark
import com.closeonjae.chesspuzzle.ui.theme.BoardLight
import com.closeonjae.chesspuzzle.ui.theme.HintTint
import com.closeonjae.chesspuzzle.ui.theme.LastMoveTint
import com.closeonjae.chesspuzzle.ui.theme.LegalDot
import com.closeonjae.chesspuzzle.ui.theme.MarkerHalo
import com.closeonjae.chesspuzzle.ui.theme.SelectedSquare
import com.closeonjae.chesspuzzle.ui.theme.TextPrimary
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
            )
            .then(
                // A candidate move that *captures*: the numbered badge below
                // sits over the piece it would take, so this accent ring
                // (inscribed in the square, same geometry as the legal-move
                // capture ring above) is what says "this one takes something"
                // without needing to see the piece underneath.
                if (candidateRank != null && piece != Piece.NONE) {
                    Modifier.drawWithContent {
                        val ringWidth = size.minDimension * 0.1f
                        drawCircle(
                            color = Accent,
                            radius = size.minDimension / 2f - ringWidth / 2f,
                            style = Stroke(width = ringWidth),
                        )
                        drawContent()
                    }
                } else {
                    Modifier
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
            // The popularity badge (user request: 인기 수는 원 안에 있는 숫자로).
            // A single rank digit rather than a percentage: a square is ~21dp
            // on the watch, so the badge circle can only hold one legible
            // digit — the exact share is one tap away in the move list.
            // MarkerHalo is what makes it read on both square colors; accent
            // alone is 3.77:1 on light squares but only 1.46:1 on dark ones
            // (DESIGN.md 9.2절).
            Box(
                modifier = Modifier
                    .fillMaxSize(OpeningDimens.CandidateMarkerRatio)
                    .background(Accent, CircleShape)
                    .border(OpeningDimens.MarkerHaloWidth, MarkerHalo, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = candidateRank.toString(), style = OpeningType.markerDigit, color = TextPrimary)
            }
        }
    }
}
