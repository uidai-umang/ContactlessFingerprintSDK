package app.gov.uidai.capture.slap.processing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.sqrt

class SlapHandRegionDetector {

    companion object {
        private const val ANALYSIS_MAX_WIDTH = 480
        private const val BORDER_MARGIN_RATIO = 0.05f
        private const val MIN_BACKGROUND_DISTANCE = 18.0

        private const val CB_MIN = 60
        private const val CB_MAX = 150
        private const val CR_MIN = 110
        private const val CR_MAX = 190

        private const val ACTIVE_THRESHOLD_RATIO = 0.15
        private const val PADDING_RATIO = 0.06f
    }

    data class Detection(
        val handRegion: RectF,
        val binaryMask: BooleanArray,
        val maskWidth: Int,
        val maskHeight: Int,
        val invScale: Float
    )

    fun detect(bitmap: Bitmap): Detection? {
        require(bitmap.width > 0 && bitmap.height > 0)

        val scale = if (bitmap.width > ANALYSIS_MAX_WIDTH) {
            ANALYSIS_MAX_WIDTH.toFloat() / bitmap.width
        } else {
            1f
        }

        val analysisBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val width = analysisBitmap.width
        val height = analysisBitmap.height

        val pixels = IntArray(width * height)
        analysisBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val cb = DoubleArray(pixels.size)
        val cr = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color).toDouble()
            val g = Color.green(color).toDouble()
            val b = Color.blue(color).toDouble()
            cb[i] = 128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b
            cr[i] = 128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b
        }

        val (backgroundCb, backgroundCr) = measureBackground(cb, cr, width, height)

        val binary = BooleanArray(pixels.size)
        for (i in pixels.indices) {
            val dCb = cb[i] - backgroundCb
            val dCr = cr[i] - backgroundCr
            val distanceFromBackground = sqrt(dCb * dCb + dCr * dCr)

            val skinPlausible = cb[i] in CB_MIN.toDouble()..CB_MAX.toDouble() &&
                    cr[i] in CR_MIN.toDouble()..CR_MAX.toDouble()

            binary[i] = skinPlausible && distanceFromBackground >= MIN_BACKGROUND_DISTANCE
        }

        val box = boundingBoxOf(binary, width, height)
        if (analysisBitmap !== bitmap) analysisBitmap.recycle()
        box ?: return null

        val invScale = 1f / scale
        val scaledBox = RectF(
            box.left * invScale,
            box.top * invScale,
            box.right * invScale,
            box.bottom * invScale
        ).paddedBy(PADDING_RATIO, bitmap.width, bitmap.height)

        return Detection(
            handRegion = scaledBox,
            binaryMask = binary,
            maskWidth = width,
            maskHeight = height,
            invScale = invScale
        )
    }

    private fun measureBackground(cb: DoubleArray, cr: DoubleArray, width: Int, height: Int): Pair<Double, Double> {
        val marginX = (width * BORDER_MARGIN_RATIO).toInt().coerceAtLeast(1)
        val marginY = (height * BORDER_MARGIN_RATIO).toInt().coerceAtLeast(1)

        val cbSamples = mutableListOf<Double>()
        val crSamples = mutableListOf<Double>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val onBorder = x < marginX || x >= width - marginX || y < marginY || y >= height - marginY
                if (onBorder) {
                    cbSamples.add(cb[y * width + x])
                    crSamples.add(cr[y * width + x])
                }
            }
        }

        return cbSamples.median() to crSamples.median()
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 128.0
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun boundingBoxOf(binary: BooleanArray, width: Int, height: Int): RectF? {
        val rowCounts = IntArray(height)
        val colCounts = IntArray(width)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                if (binary[offset + x]) {
                    rowCounts[y]++
                    colCounts[x]++
                }
            }
        }

        val rowPeak = rowCounts.maxOrNull() ?: 0
        val colPeak = colCounts.maxOrNull() ?: 0
        if (rowPeak == 0 || colPeak == 0) return null

        val rowThreshold = rowPeak * ACTIVE_THRESHOLD_RATIO
        val colThreshold = colPeak * ACTIVE_THRESHOLD_RATIO

        val top = (0 until height).firstOrNull { rowCounts[it] >= rowThreshold } ?: return null
        val bottom = (height - 1 downTo 0).firstOrNull { rowCounts[it] >= rowThreshold } ?: return null
        val left = (0 until width).firstOrNull { colCounts[it] >= colThreshold } ?: return null
        val right = (width - 1 downTo 0).firstOrNull { colCounts[it] >= colThreshold } ?: return null

        if (right <= left || bottom <= top) return null

        return RectF(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
    }

    private fun RectF.paddedBy(ratio: Float, maxWidth: Int, maxHeight: Int): RectF {
        val padX = width() * ratio
        val padY = height() * ratio
        return RectF(
            (left - padX).coerceAtLeast(0f),
            (top - padY).coerceAtLeast(0f),
            (right + padX).coerceAtMost(maxWidth.toFloat()),
            (bottom + padY).coerceAtMost(maxHeight.toFloat())
        )
    }
}