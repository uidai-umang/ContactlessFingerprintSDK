package app.gov.uidai.capture.utils.extension

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs

/**
 * Calculates the area of this RectF.
 *
 * @return The area as a Float (width * height). Returns 0 if either dimension is non-positive.
 */
fun RectF.area(): Float {
    return abs(width() * height())
}

/**
 * Returns true if this RectF is completely inside the other RectF.
 *
 * @param outer the RectF that should fully contain this one
 * @return true if left, top, right, bottom of this RectF all lie within outer
 */
fun RectF.isInside(outer: RectF): Boolean {
    val offset = 100
    return (outer.left - offset) <= this.left &&
            (outer.top - offset) <= this.top &&
            (outer.right + offset) >= this.right &&
            (outer.bottom  + offset) >= this.bottom
}


fun RectF.rotateACW(currentImageWidth: Int, currentImageHeight: Int, rotation: Int): RectF {
    return when (rotation) {
        90 -> {
            // 90 degrees anti-clockwise: (x,y) -> (y, effectiveWidth - x)
            RectF(
                top,
                currentImageWidth - right,
                bottom,
                currentImageWidth - left
            )
        }
        180 -> {
            // 180 degrees: (x,y) -> (effectiveWidth - x, effectiveHeight - y)
            RectF(
                currentImageWidth - right,
                currentImageHeight - bottom,
                currentImageWidth - left,
                currentImageHeight - top
            )
        }
        270 -> {
            // 270 degrees anti-clockwise: (x,y) -> (effectiveHeight - y, x)
            RectF(
                currentImageHeight - bottom,
                left,
                currentImageHeight - top,
                right
            )
        }
        else -> {
            // 0 degrees or default - no transformation needed
            this
        }
    }
}

/** Point analog of RectF.rotateACW() above -- same per-corner mapping, one point at a time. */
fun PointF.rotateACW(currentImageWidth: Int, currentImageHeight: Int, rotation: Int): PointF {
    return when (rotation) {
        90 -> PointF(y, currentImageWidth - x)
        180 -> PointF(currentImageWidth - x, currentImageHeight - y)
        270 -> PointF(currentImageHeight - y, x)
        else -> this
    }
}

/**
 * Expands this RectF on all sides by [percent] of its own width/height --
 * used to pad a tight landmark bounding box (e.g. a slap capture's hand
 * box) so the delivered crop isn't pixel-tight to the detected box.
 */
fun RectF.inflatedByPercent(percent: Float): RectF {
    val dx = width() * percent
    val dy = height() * percent
    return RectF(left - dx, top - dy, right + dx, bottom + dy)
}