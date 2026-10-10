package app.gov.uidai.capture.slap.processing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.max
import kotlin.math.min

class SlapFingerBandDetector {

    companion object {
        private val TAG = SlapFingerBandDetector::class.simpleName
        const val FINGER_COUNT = 4

        private const val ROW_SMOOTHING_RADIUS = 8
        private const val MIN_FINGER_HEIGHT_RATIO = 0.04f
        private const val MAX_FINGER_HEIGHT_RATIO = 0.20f
        private const val MIN_BAND_FILL_RATIO = 0.12f
        private const val RELATIVE_ACTIVE_THRESHOLD_RATIO = 0.20

        private const val TIP_SCAN_TOP_RATIO = 0.20f
        private const val TIP_SCAN_BOTTOM_RATIO = 0.80f

        private const val FINGERPRINT_ROI_LENGTH_MULTIPLIER = 2.2f
        private const val FINGERPRINT_ROI_VERTICAL_PADDING_RATIO = 0.08f

        private const val CB_MIN = 70
        private const val CB_MAX = 135
        private const val CR_MIN = 125
        private const val CR_MAX = 180
    }

    enum class HandType { LEFT, RIGHT }

    data class Fingertip(val point: PointF, val confidence: Float)

    data class BandResult(
        val fingerBand: RectF,
        val fingertip: Fingertip,
        val fingerprintRegion: RectF
    )

    data class DetectionResult(
        val bands: List<BandResult>,
        val analysisWidth: Int,
        val analysisHeight: Int
    )

    // Original per-frame entry point: builds its OWN mask using the fixed
    // Cb/Cr classifySkin() range, then delegates to detectFromBinaryMask.
    fun detect(
        bitmap: Bitmap,
        handType: HandType,
        maxAnalysisWidth: Int? = null
    ): DetectionResult {

        require(bitmap.width > 0 && bitmap.height > 0)

        val scale = if (maxAnalysisWidth != null && bitmap.width > maxAnalysisWidth) {
            maxAnalysisWidth.toFloat() / bitmap.width
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

        val (cb, cr) = extractChromaChannels(analysisBitmap)
        val blurredCb = gaussianBlur(cb, width, height)
        val blurredCr = gaussianBlur(cr, width, height)
        val binary = classifySkin(blurredCb, blurredCr)

        val skinRatio = binary.count { it }.toFloat() / binary.size
        Log.d(TAG, "skin mask: %.1f%% of analysis frame classified as skin".format(skinRatio * 100))

        if (analysisBitmap !== bitmap) {
            analysisBitmap.recycle()
        }

        val invScale = 1f / scale
        val result = detectFromBinaryMask(binary, width, height, handType, invScale)

        Log.d(
            TAG,
            "detect(): ${result.bands.size}/${FINGER_COUNT} finger bands found " +
                    "(analysis ${width}x${height})"
        )

        return result
    }

    private fun RectF.scaledBy(factor: Float): RectF =
        RectF(left * factor, top * factor, right * factor, bottom * factor)

    // Shared band -> fingertip -> ROI pipeline. Runs on WHATEVER binary
    // mask it's handed -- detect() feeds it the fixed-Cb/Cr mask it just
    // built; SlapFingerprintProcessor feeds it SlapHandRegionDetector's
    // background-relative mask instead.
    fun detectFromBinaryMask(
        binary: BooleanArray,
        width: Int,
        height: Int,
        handType: HandType,
        invScale: Float = 1f
    ): DetectionResult {

        val fingerBands = detectFingerBands(binary, width, height)

        val bands = fingerBands.map { band ->
            val fingertip = detectFingertip(binary, width, height, band, handType)
            val fingerprintRegion = calculateFingerprintRegion(
                fingerBand = band,
                fingertip = fingertip.point,
                handType = handType
            )
            BandResult(fingerBand = band, fingertip = fingertip, fingerprintRegion = fingerprintRegion)
        }

        val scaledBands = if (invScale == 1f) {
            bands
        } else {
            bands.map { result ->
                BandResult(
                    fingerBand = result.fingerBand.scaledBy(invScale),
                    fingertip = Fingertip(
                        point = PointF(
                            result.fingertip.point.x * invScale,
                            result.fingertip.point.y * invScale
                        ),
                        confidence = result.fingertip.confidence
                    ),
                    fingerprintRegion = result.fingerprintRegion.scaledBy(invScale)
                )
            }
        }

        return DetectionResult(
            bands = scaledBands,
            analysisWidth = width,
            analysisHeight = height
        )
    }

    private fun extractChromaChannels(bitmap: Bitmap): Pair<IntArray, IntArray> {
        val width = bitmap.width
        val height = bitmap.height

        val source = IntArray(width * height)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)

        val cb = IntArray(source.size)
        val cr = IntArray(source.size)
        for (i in source.indices) {
            val color = source[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            cb[i] = (128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b).toInt().coerceIn(0, 255)
            cr[i] = (128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b).toInt().coerceIn(0, 255)
        }
        return cb to cr
    }

    private fun classifySkin(cb: IntArray, cr: IntArray): BooleanArray =
        BooleanArray(cb.size) { i -> cb[i] in CB_MIN..CB_MAX && cr[i] in CR_MIN..CR_MAX }

    private fun gaussianBlur(pixels: IntArray, width: Int, height: Int): IntArray {
        val kernel = intArrayOf(1, 4, 6, 4, 1)
        val horizontal = IntArray(pixels.size)
        val output = IntArray(pixels.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                for (k in -2..2) {
                    val xx = (x + k).coerceIn(0, width - 1)
                    sum += pixels[y * width + xx] * kernel[k + 2]
                }
                horizontal[y * width + x] = sum / 16
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                for (k in -2..2) {
                    val yy = (y + k).coerceIn(0, height - 1)
                    sum += horizontal[yy * width + x] * kernel[k + 2]
                }
                output[y * width + x] = sum / 16
            }
        }

        return output
    }

    private fun detectFingerBands(binary: BooleanArray, width: Int, height: Int): List<RectF> {
        val rowProjection = DoubleArray(height)

        for (y in 0 until height) {
            var count = 0
            val offset = y * width
            for (x in 0 until width) {
                if (binary[offset + x]) count++
            }
            rowProjection[y] = count.toDouble()
        }

        val smoothed = smooth(rowProjection, ROW_SMOOTHING_RADIUS)

        val maxProjection = smoothed.maxOrNull() ?: return emptyList()
        if (maxProjection <= 0.0) return emptyList()

        val activeThreshold = maxProjection * RELATIVE_ACTIVE_THRESHOLD_RATIO
        val minAbsoluteFill = width * MIN_BAND_FILL_RATIO

        val candidates = mutableListOf<Pair<Int, Int>>()
        var start = -1

        for (y in 0 until height) {
            val active = smoothed[y] >= activeThreshold

            if (active && start == -1) start = y

            if ((!active || y == height - 1) && start != -1) {
                val end = if (!active) y - 1 else y
                val bandHeight = end - start + 1
                val minHeight = (height * MIN_FINGER_HEIGHT_RATIO).toInt()
                val maxHeight = (height * MAX_FINGER_HEIGHT_RATIO).toInt()

                val bandScore = smoothed.slice(start..end).average()

                if (bandHeight >= minHeight && bandHeight <= maxHeight && bandScore >= minAbsoluteFill) {
                    candidates.add(start to end)
                }
                start = -1
            }
        }

        val merged = mergeNearbyBands(candidates, height)

        val selected = merged
            .map { candidate ->
                val score = smoothed.slice(candidate.first..candidate.second).average()
                Triple(candidate.first, candidate.second, score)
            }
            .sortedByDescending { it.third }
            .take(FINGER_COUNT)
            .sortedBy { it.first }
            .map { it.first to it.second }

        return selected.map { candidate ->
            RectF(0f, candidate.first.toFloat(), width.toFloat(), (candidate.second + 1).toFloat())
        }
    }

    private fun smooth(values: DoubleArray, radius: Int): DoubleArray {
        val result = DoubleArray(values.size)
        for (i in values.indices) {
            val start = max(0, i - radius)
            val end = min(values.lastIndex, i + radius)
            var sum = 0.0
            var count = 0
            for (j in start..end) {
                sum += values[j]
                count++
            }
            result[i] = sum / count
        }
        return result
    }

    private fun mergeNearbyBands(candidates: List<Pair<Int, Int>>, height: Int): List<Pair<Int, Int>> {
        if (candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedBy { it.first }
        val result = mutableListOf<Pair<Int, Int>>()

        var currentStart = sorted.first().first
        var currentEnd = sorted.first().second

        val gapThreshold = (height * 0.015f).toInt().coerceAtLeast(2)

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.first - currentEnd <= gapThreshold) {
                currentEnd = max(currentEnd, next.second)
            } else {
                result.add(currentStart to currentEnd)
                currentStart = next.first
                currentEnd = next.second
            }
        }
        result.add(currentStart to currentEnd)

        return result
    }

    private fun detectFingertip(
        binary: BooleanArray,
        width: Int,
        height: Int,
        fingerBand: RectF,
        handType: HandType
    ): Fingertip {

        val top = fingerBand.top.toInt().coerceIn(0, height - 1)
        val bottom = fingerBand.bottom.toInt().coerceIn(top + 1, height)
        val bandHeight = bottom - top

        val scanTop = top + (bandHeight * TIP_SCAN_TOP_RATIO).toInt()
        val scanBottom = top + (bandHeight * TIP_SCAN_BOTTOM_RATIO).toInt()

        val boundaryXs = mutableListOf<Int>()

        for (y in scanTop until scanBottom) {
            var boundary = if (handType == HandType.LEFT) -1 else width

            if (handType == HandType.LEFT) {
                for (x in width - 1 downTo 0) {
                    if (binary[y * width + x]) {
                        boundary = x
                        break
                    }
                }
            } else {
                for (x in 0 until width) {
                    if (binary[y * width + x]) {
                        boundary = x
                        break
                    }
                }
            }

            if ((handType == HandType.LEFT && boundary >= 0) ||
                (handType == HandType.RIGHT && boundary < width)
            ) {
                boundaryXs.add(boundary)
            }
        }

        if (boundaryXs.isEmpty()) {
            val fallbackX = width * 0.5f
            return Fingertip(point = PointF(fallbackX, (top + bottom) / 2f), confidence = 0f)
        }

        boundaryXs.sort()

        val filtered = if (boundaryXs.size >= 5) {
            val trim = (boundaryXs.size * 0.10).toInt()
            boundaryXs.subList(trim, boundaryXs.size - trim)
        } else {
            boundaryXs
        }

        val medianX = filtered[filtered.size / 2]
        val tipY = (top + bottom) / 2f

        val mean = filtered.average()
        var variance = 0.0
        for (x in filtered) {
            val diff = x - mean
            variance += diff * diff
        }
        variance /= filtered.size

        val standardDeviation = kotlin.math.sqrt(variance)

        val confidence = (1.0 - standardDeviation / (fingerBand.height() * 0.5))
            .coerceIn(0.0, 1.0)
            .toFloat()

        return Fingertip(point = PointF(medianX.toFloat(), tipY), confidence = confidence)
    }

    private fun calculateFingerprintRegion(
        fingerBand: RectF,
        fingertip: PointF,
        handType: HandType
    ): RectF {

        val fingerHeight = fingerBand.height()
        val roiLength = (fingerHeight * FINGERPRINT_ROI_LENGTH_MULTIPLIER)
            .coerceAtLeast(1f)
            .coerceAtMost(fingerBand.width() * 0.5f)
        val verticalPadding = fingerHeight * FINGERPRINT_ROI_VERTICAL_PADDING_RATIO

        val top = fingerBand.top + verticalPadding
        val bottom = fingerBand.bottom - verticalPadding

        return when (handType) {
            HandType.LEFT -> RectF(
                (fingertip.x - roiLength).coerceAtLeast(fingerBand.left),
                top,
                fingertip.x,
                bottom
            )

            HandType.RIGHT -> RectF(
                fingertip.x,
                top,
                (fingertip.x + roiLength).coerceAtMost(fingerBand.right),
                bottom
            )
        }
    }
}