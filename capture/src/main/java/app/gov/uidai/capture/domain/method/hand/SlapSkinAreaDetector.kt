package app.gov.uidai.capture.domain.method.hand

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.utils.logExecutionTime
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class SlapSkinAreaDetector @Inject constructor() {

    companion object {
        private val TAG = SlapSkinAreaDetector::class.simpleName
        private const val ANALYSIS_WIDTH = 160
        private const val CR_MIN = 133
        private const val CR_MAX = 173
        private const val CB_MIN = 77
        private const val CB_MAX = 127
        private const val MIN_AREA_RATIO_FOR_DETECTION = 0.03f
    }

    fun detectSkinArea(bitmap: Bitmap): SlapFrameResult = logExecutionTime(TAG, "detectSkinArea") {
        val scale = ANALYSIS_WIDTH.toFloat() / bitmap.width
        val analysisWidth = ANALYSIS_WIDTH
        val analysisHeight = max(1, (bitmap.height * scale).toInt())

        val small = Bitmap.createScaledBitmap(bitmap, analysisWidth, analysisHeight, false)
        val pixels = IntArray(analysisWidth * analysisHeight)
        small.getPixels(pixels, 0, analysisWidth, 0, 0, analysisWidth, analysisHeight)

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        var skinPixelCount = 0

        for (y in 0 until analysisHeight) {
            for (x in 0 until analysisWidth) {
                val pixel = pixels[y * analysisWidth + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val cr = (128 + (112.439 * r - 94.154 * g - 18.285 * b) / 256).toInt()
                val cb = (128 + (-37.945 * r - 74.494 * g + 112.439 * b) / 256).toInt()

                if (cr in CR_MIN..CR_MAX && cb in CB_MIN..CB_MAX) {
                    skinPixelCount++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        small.recycle()

        val totalPixels = analysisWidth * analysisHeight
        val areaRatio = skinPixelCount.toFloat() / totalPixels

        if (areaRatio < MIN_AREA_RATIO_FOR_DETECTION || maxX < minX || maxY < minY) {
            return@logExecutionTime SlapFrameResult(
                handDetected = false,
                areaRatio = areaRatio,
                fingertips = emptyList(),
                box = null
            )
        }

        val invScale = 1f / scale
        val box = RectF(
            minX * invScale,
            minY * invScale,
            (maxX + 1) * invScale,
            (maxY + 1) * invScale
        )

        SlapFrameResult(
            handDetected = true,
            areaRatio = areaRatio,
            fingertips = emptyList(),
            box = box
        )
    }
}