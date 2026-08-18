package com.closeonjae.chesspuzzle.ui.board

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

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
 *
 * Shared by the puzzle and opening screens' right-hand tabs — moved here out
 * of PuzzleScreen so both screens' side tabs keep the identical silhouette
 * (DESIGN.md 9절: same slot, same shape, only the action differs).
 */
object HalfMoonShape : Shape {
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
object HintTabShape : Shape {
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
