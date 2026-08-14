package app.gov.uidai.capture.usecase

import android.graphics.RectF

object CutoutRectUtils {
    fun isValid(rect: RectF): Boolean =
        !rect.left.isNaN() && !rect.top.isNaN() &&
                !rect.right.isNaN() && !rect.bottom.isNaN() &&
                rect.left.isFinite() && rect.top.isFinite() &&
                rect.right.isFinite() && rect.bottom.isFinite() &&
                rect.width() > 0f && rect.height() > 0f
}