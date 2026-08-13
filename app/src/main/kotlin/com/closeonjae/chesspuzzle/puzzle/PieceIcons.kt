package com.closeonjae.chesspuzzle.puzzle

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import com.closeonjae.chesspuzzle.ui.theme.PieceBlackFill
import com.closeonjae.chesspuzzle.ui.theme.PieceOutline
import com.closeonjae.chesspuzzle.ui.theme.PieceWhiteFill
import com.github.bhlangonijr.chesslib.PieceType

/**
 * Original rounded-icon-style piece artwork, inspired by a reference image
 * the user shared — hand-drawn to capture the same chunky/friendly feel,
 * not traced from it (that specific icon set's license is unknown, so this
 * is deliberately our own shapes — see DESIGN.md 2절).
 *
 * Each piece is a small list of primitives in a shared 24x24 unit box, so
 * one definition serves both colors: [PieceIcon] fills+strokes them per
 * side rather than baking color into the shape data.
 */
private sealed interface PiecePrimitive
private data class PathPrimitive(val d: String) : PiecePrimitive
private data class RectPrimitive(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : PiecePrimitive
private data class CirclePrimitive(val cx: Float, val cy: Float, val r: Float) : PiecePrimitive

private val PawnShape = listOf(
    CirclePrimitive(12f, 7.2f, 3.1f),
    PathPrimitive("M8.6 12c-.35 1.4-1.6 2.9-1.6 5.3 0 .9.5 1.4 1 1.4h8c.5 0 1-.5 1-1.4 0-2.4-1.25-3.9-1.6-5.3-1 .75-2.2 1.1-3.4 1.1s-2.4-.35-3.4-1.1z"),
    RectPrimitive(6f, 19.2f, 12f, 2.3f, 1.15f),
)
private val KnightShape = listOf(
    PathPrimitive(
        "M7.2 21v-3.3c0-1.1.5-1.9 1.15-2.5l-1.55-.65c-.7-.3-.85-1.05-.35-1.65l1.2-1.45c-.55-1.35-.4-2.9.4-4.1.95-1.4 2.6-2.15 4.1-2.05.95.05 1.75.4 2.3 1 .55-.3 1.2-.4 1.85-.25.9.2 1.5 1 1.5 1.95v1.7c0 .85-.35 1.65-.95 2.2l-2.35 2.15c.85.6 1.4 1.6 1.4 2.7V21z",
    ),
)
private val BishopShape = listOf(
    CirclePrimitive(12f, 4.1f, 1.35f),
    PathPrimitive(
        "M12 6.1c-2.3 1.65-3.55 3.75-3.55 6.5 0 2.05.95 3.7 1.85 4.9-.45.6-.75 1.35-.75 2.25 0 .6.2 1.05.5 1.35h7.9c.3-.3.5-.75.5-1.35 0-.9-.3-1.65-.75-2.25.9-1.2 1.85-2.85 1.85-4.9 0-2.75-1.25-4.85-3.55-6.5l1-1-2-2-2 2z",
    ),
    RectPrimitive(6.7f, 20f, 10.6f, 2.1f, 1.05f),
)
private val RookShape = listOf(
    PathPrimitive("M6.3 3h2.3v1.9h2.1V3h2.6v1.9h2.1V3h2.3v4.2l-1.4 1.4v9l1.4 1.4V21H6.3v-2l1.4-1.4v-9L6.3 7.2z"),
    RectPrimitive(6f, 20.6f, 12f, 1.9f, 0.95f),
)
private val QueenShape = listOf(
    PathPrimitive("M5.6 8.6l1.75 5.7L8.5 10.2l1.9 3.15L12 8.9l1.6 4.45 1.9-3.15 1.15 4.1 1.75-5.7-.9 7.9H6.5z"),
    CirclePrimitive(5.6f, 7.6f, 1.15f),
    CirclePrimitive(9.4f, 6.3f, 1.05f),
    CirclePrimitive(12f, 5.8f, 1.1f),
    CirclePrimitive(14.6f, 6.3f, 1.05f),
    CirclePrimitive(18.4f, 7.6f, 1.15f),
    PathPrimitive("M6.9 16.5h10.2l.55 2.15c.15.55-.25 1.1-.85 1.1H7.2c-.6 0-1-.55-.85-1.1z"),
    RectPrimitive(6f, 20.4f, 12f, 1.9f, 0.95f),
)
private val KingShape = listOf(
    RectPrimitive(11.1f, 1.3f, 1.8f, 4f, 0.5f),
    RectPrimitive(10f, 2.6f, 4f, 1.6f, 0.5f),
    PathPrimitive("M7.1 8.3h9.8l1.05 5.4-1.5 1.4v3.1H7.55v-3.1l-1.5-1.4z"),
    RectPrimitive(6f, 20.4f, 12f, 1.9f, 0.95f),
)

private fun shapeFor(pieceType: PieceType): List<PiecePrimitive>? = when (pieceType) {
    PieceType.PAWN -> PawnShape
    PieceType.KNIGHT -> KnightShape
    PieceType.BISHOP -> BishopShape
    PieceType.ROOK -> RookShape
    PieceType.QUEEN -> QueenShape
    PieceType.KING -> KingShape
    else -> null
}

/** Renders one piece into [modifier]'s bounds, scaled from the shared 24x24 unit box. */
@Composable
fun PieceIcon(pieceType: PieceType, isWhite: Boolean, modifier: Modifier = Modifier) {
    val shapes = shapeFor(pieceType) ?: return
    val fill = if (isWhite) PieceWhiteFill else PieceBlackFill
    // White pieces get a dark outline for definition; black pieces' outline
    // matches their own fill (effectively none) — same as the reference.
    val stroke = if (isWhite) PieceOutline else PieceBlackFill
    Canvas(modifier = modifier) {
        val unitScale = size.minDimension / 24f
        scale(scaleX = unitScale, scaleY = unitScale, pivot = Offset.Zero) {
            shapes.forEach { primitive ->
                drawPrimitive(primitive, fill, stroke)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPrimitive(
    primitive: PiecePrimitive,
    fill: Color,
    stroke: Color,
) {
    val strokeWidth = 0.9f
    when (primitive) {
        is PathPrimitive -> {
            val path = PathParser().parsePathString(primitive.d).toPath()
            drawPath(path, color = fill)
            drawPath(path, color = stroke, style = Stroke(width = strokeWidth))
        }
        is RectPrimitive -> {
            val topLeft = Offset(primitive.x, primitive.y)
            val rectSize = Size(primitive.w, primitive.h)
            val corner = CornerRadius(primitive.r, primitive.r)
            drawRoundRect(color = fill, topLeft = topLeft, size = rectSize, cornerRadius = corner)
            drawRoundRect(color = stroke, topLeft = topLeft, size = rectSize, cornerRadius = corner, style = Stroke(width = strokeWidth))
        }
        is CirclePrimitive -> {
            val center = Offset(primitive.cx, primitive.cy)
            drawCircle(color = fill, radius = primitive.r, center = center)
            drawCircle(color = stroke, radius = primitive.r, center = center, style = Stroke(width = strokeWidth))
        }
    }
}
