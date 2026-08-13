package com.closeonjae.chesspuzzle.puzzle

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import com.closeonjae.chesspuzzle.R
import com.github.bhlangonijr.chesslib.PieceType

/**
 * Piece artwork: alpha-masked PNGs cut from the user-provided LS/DS
 * reference images (each square-color PNG there is a piece flattened onto
 * a solid square background, no transparency) — DESIGN.md 2절. For each
 * piece color we keyed the cutout from whichever square shade contrasts
 * more with that piece's own fill (white pieces from the DS/blue set,
 * black pieces from the LS/pale set) for the cleanest edges, then
 * color-decontaminated the anti-aliased edge pixels against that known
 * background so they composite cleanly onto either square color. Kept as
 * standalone piece bitmaps (not baked into the square background) so a
 * piece can still be moved/animated independently of the square under it.
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
        // painterResource's Image() overload draws at the default (Low =
        // plain bilinear, no mipmap) filter quality, which visibly aliased
        // the piece outlines once shrunk down to board-square size — the
        // bitmap overload lets us ask for FilterQuality.High instead.
        bitmap = ImageBitmap.imageResource(id = drawableId),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
    )
}
