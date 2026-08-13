package com.closeonjae.chesspuzzle.puzzle

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.closeonjae.chesspuzzle.R
import com.github.bhlangonijr.chesslib.PieceType

/**
 * Piece artwork: alpha-masked PNGs cut from a real chess icon set the user
 * provided, pre-scaled and anti-aliased for on-screen size at build time —
 * DESIGN.md 2절 for the full extraction history. `res/drawable-nodpi/
 * piece_*.png` is the single source now; there's no vector fallback and no
 * further runtime processing. Kept as standalone piece bitmaps (not baked
 * into the square background) so a piece can still be moved/animated
 * independently of the square under it.
 */
private fun drawableFor(pieceType: PieceType, isWhite: Boolean): Int = if (isWhite) {
    when (pieceType) {
        PieceType.PAWN -> R.drawable.piece_wp
        PieceType.KNIGHT -> R.drawable.piece_wn
        PieceType.BISHOP -> R.drawable.piece_wb
        PieceType.ROOK -> R.drawable.piece_wr
        PieceType.QUEEN -> R.drawable.piece_wq
        PieceType.KING -> R.drawable.piece_wk
        else -> 0
    }
} else {
    when (pieceType) {
        PieceType.PAWN -> R.drawable.piece_bp
        PieceType.KNIGHT -> R.drawable.piece_bn
        PieceType.BISHOP -> R.drawable.piece_bb
        PieceType.ROOK -> R.drawable.piece_br
        PieceType.QUEEN -> R.drawable.piece_bq
        PieceType.KING -> R.drawable.piece_bk
        else -> 0
    }
}

/** Renders one piece into [modifier]'s bounds. */
@Composable
fun PieceIcon(pieceType: PieceType, isWhite: Boolean, modifier: Modifier = Modifier) {
    val drawableId = drawableFor(pieceType, isWhite)
    if (drawableId == 0) return
    Image(
        painter = painterResource(id = drawableId),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
