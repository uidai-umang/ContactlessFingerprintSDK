package app.gov.uidai.capture.ui.camera.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.graphics.scale
import app.gov.uidai.capture.utils.extension.rotate
import kotlin.math.max

class OverlayMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var borderBitmap: Bitmap? = null

    fun updateMask(bmp: Bitmap?, rotation: Int) {
        borderBitmap = bmp?.rotate(rotation)?.let {
            val scaleX = width * 1f / it.width
            val scaleY = height * 1f / it.height
            val scaleFactor = max(scaleX, scaleY)
            Log.d("OverlayMaskView", "ScaleX: $scaleX, ScaleY: $scaleY")
            val scaleWidth = (it.width * scaleFactor).toInt()
            val scaleHeight = (it.height * scaleFactor).toInt()
            it.scale(scaleWidth, scaleHeight)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        borderBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
    }
}