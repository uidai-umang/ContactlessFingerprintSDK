package app.gov.uidai.capture.slap.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import app.gov.uidai.capture.domain.method.final_processing.FinalProcessingU2Net
import app.gov.uidai.capture.domain.method.segmentation.U2NetFloat32
import kotlin.math.max
import kotlin.math.min

/**
 * [context] is required now because U2NetFloat32 needs it to load the
 * TFLite model. No new segmentation/ridge classes were added -- this
 * reuses U2NetFloat32 and FinalProcessingU2Net EXACTLY as single capture
 * does, just applied to a per-finger ROI instead of the whole cutout.
 */
class SlapFingerprintProcessor(
    private val context: Context
) {

    companion object {

        private const val TAG = "SlapFingerprintProcessor"

        private const val FINGER_COUNT = 4

        // Reuses the SAME model weights single capture uses -- no
        // slap-trained model exists. Whether these weights, presumably
        // trained on single-finger framing, generalise to a ROI cropped
        // FROM a slap image is UNVERIFIED -- test on real captures.
        private const val U2NET_MODEL_ASSET_PATH = "u2net_320x320_float32.tflite"

        /*
         * Finger detection
         */
        private const val GAUSSIAN_BLUR_RADIUS = 5

        private const val ROW_SMOOTHING_RADIUS = 8

        private const val MIN_FINGER_HEIGHT_RATIO = 0.04f
        private const val MAX_FINGER_HEIGHT_RATIO = 0.30f

        /*
         * Fingertip detection
         *
         * We don't use the entire band.
         * We inspect the middle portion of the finger
         * where the fingertip boundary is cleanest.
         */
        private const val TIP_SCAN_TOP_RATIO = 0.20f
        private const val TIP_SCAN_BOTTOM_RATIO = 0.80f

        /*
         * Remove tiny foreground runs caused by noise.
         */
        private const val MIN_FOREGROUND_RUN_RATIO = 0.08f

        /*
         * Fingerprint ROI
         *
         * The fingerprint area is BEHIND the fingertip,
         * not the fingertip point itself.
         *
         * ROI length is expressed relative to finger thickness.
         */
        private const val FINGERPRINT_ROI_LENGTH_MULTIPLIER = 2.2f

        private const val FINGERPRINT_ROI_VERTICAL_PADDING_RATIO = 0.08f
    }

    // Lazy so the TFLite interpreter only loads once this processor is
    // actually used.
    private val u2Net: U2NetFloat32 by lazy {
        U2NetFloat32(context, U2NET_MODEL_ASSET_PATH)
    }

    enum class HandType {
        LEFT,
        RIGHT
    }

    data class Fingertip(
        val point: PointF,
        val confidence: Float
    )

    data class FingerResult(
        val index: Int,

        /*
         * Original horizontal finger band.
         */
        val fingerBand: RectF,

        /*
         * Detected fingertip.
         */
        val fingertip: Fingertip,

        /*
         * Candidate fingerprint-bearing region.
         */
        val fingerprintRegion: RectF,

        val fingerprintRoi: Bitmap,

        /*
         * U2Net's mask, cropped to its own box -- null if segmentation
         * found nothing. Kept for diagnostics; FinalProcessingU2Net
         * already consumed it to produce ridgeImage below.
         */
        val segmentationMask: Bitmap?,

        /*
         * FinalProcessingU2Net.run() output -- same call single capture
         * already uses. Falls back to the unmodified ROI if segmentation
         * found nothing (FinalProcessingU2Net's own documented behaviour
         * for a null mask).
         */
        val ridgeImage: Bitmap
    )

    data class Result(
        val fingers: List<FingerResult>,
        val diagnosticImage: Bitmap
    )

    /**
     * Main Slap processing flow.
     *
     *     Original bitmap
     *          ↓
     *     Grayscale -> Otsu -> row projection -> 4 finger bands
     *          ↓
     *     Fingertip detection (per band)
     *          ↓
     *     Fingerprint region -> ROI crop (per finger)
     *          ↓
     *     U2Net segmentation on the ROI (per finger)
     *          ↓
     *     FinalProcessingU2Net ridge extraction (per finger)
     */
    fun process(
        bitmap: Bitmap,
        handType: HandType
    ): Result {

        require(bitmap.width > 0 && bitmap.height > 0)

        val grayscale = toGrayscale(bitmap)

        val blurred = gaussianBlur(
            pixels = grayscale,
            width = bitmap.width,
            height = bitmap.height
        )

        val otsu = otsuBinary(
            pixels = blurred
        )

        val fingerBands = detectFingerBands(
            binary = otsu.binary,
            width = bitmap.width,
            height = bitmap.height
        )

        val fingers = fingerBands.mapIndexed { index, band ->

            val fingertip = detectFingertip(
                binary = otsu.binary,
                width = bitmap.width,
                height = bitmap.height,
                fingerBand = band,
                handType = handType
            )

            val fingerprintRegion =
                calculateFingerprintRegion(
                    fingerBand = band,
                    fingertip = fingertip.point,
                    handType = handType
                )

            val roi = cropBitmap(
                bitmap = bitmap,
                rect = fingerprintRegion
            )

            /*
             * ---------------------------------------------------------
             * U2Net segmentation + FinalProcessingU2Net ridge
             * extraction -- both reused UNMODIFIED from single capture.
             * ---------------------------------------------------------
             */
            val segResult = try {
                u2Net.run(roi)
            } catch (e: Exception) {
                null
            }

            val (refinedRoi, mask) = if (segResult != null) {
                // U2Net's mask is sized to ITS OWN box, not the full roi
                // -- crop roi to that same box so image and mask line
                // up, exactly like AutoCaptureImageProcessor does for
                // single capture's finalBitmap/finalMask.
                cropBitmap(roi, segResult.box) to segResult.mask
            } else {
                roi to null
            }

            val ridgeImage = FinalProcessingU2Net.run(refinedRoi, mask)

            FingerResult(
                index = index,
                fingerBand = band,
                fingertip = fingertip,
                fingerprintRegion = fingerprintRegion,
                fingerprintRoi = roi,
                segmentationMask = mask,
                ridgeImage = ridgeImage
            )
        }

        val diagnostic = createDiagnosticImage(
            bitmap = bitmap,
            fingers = fingers,
            handType = handType
        )

        return Result(
            fingers = fingers,
            diagnosticImage = diagnostic
        )
    }

    // ========================================================================
    // GRAYSCALE
    // ========================================================================

    private fun toGrayscale(
        bitmap: Bitmap
    ): IntArray {

        val width = bitmap.width
        val height = bitmap.height

        val source = IntArray(
            width * height
        )

        bitmap.getPixels(
            source,
            0,
            width,
            0,
            0,
            width,
            height
        )

        return IntArray(
            source.size
        ) { index ->

            val color = source[index]

            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            (
                    0.299 * r +
                            0.587 * g +
                            0.114 * b
                    )
                .toInt()
                .coerceIn(0, 255)
        }
    }

    // ========================================================================
    // GAUSSIAN BLUR
    // ========================================================================

    private fun gaussianBlur(
        pixels: IntArray,
        width: Int,
        height: Int
    ): IntArray {

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

    // ========================================================================
    // OTSU (for hand/band detection -- unrelated to segmentation now)
    // ========================================================================

    private data class OtsuResult(
        val binary: BooleanArray,
        val threshold: Int
    )

    private fun otsuBinary(
        pixels: IntArray
    ): OtsuResult {

        val histogram = IntArray(256)

        for (pixel in pixels) {
            histogram[pixel.coerceIn(0, 255)]++
        }

        val total = pixels.size

        var totalSum = 0.0
        for (i in 0..255) totalSum += i * histogram[i]

        var backgroundWeight = 0
        var backgroundSum = 0.0
        var bestVariance = -1.0
        var bestThreshold = 0

        for (threshold in 0..255) {
            backgroundWeight += histogram[threshold]
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break

            backgroundSum += threshold * histogram[threshold]
            val backgroundMean = backgroundSum / backgroundWeight
            val foregroundMean = (totalSum - backgroundSum) / foregroundWeight
            val difference = backgroundMean - foregroundMean

            val variance = backgroundWeight.toDouble() * foregroundWeight.toDouble() * difference * difference
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = threshold
            }
        }

        val binary = BooleanArray(pixels.size) { index -> pixels[index] < bestThreshold }

        return OtsuResult(binary = binary, threshold = bestThreshold)
    }

    // ========================================================================
    // FINGER BANDS
    // ========================================================================

    private fun detectFingerBands(
        binary: BooleanArray,
        width: Int,
        height: Int
    ): List<RectF> {

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

        val activeThreshold = maxProjection * 0.20

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

                if (bandHeight >= minHeight && bandHeight <= maxHeight) {
                    candidates.add(start to end)
                }
                start = -1
            }
        }

        val merged = mergeNearbyBands(candidates, height)

        val selected = if (merged.size <= FINGER_COUNT) {
            merged
        } else {
            merged
                .map { candidate ->
                    val score = smoothed.slice(candidate.first..candidate.second).average()
                    Triple(candidate.first, candidate.second, score)
                }
                .sortedByDescending { it.third }
                .take(FINGER_COUNT)
                .sortedBy { it.first }
                .map { it.first to it.second }
        }

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

    private fun mergeNearbyBands(
        candidates: List<Pair<Int, Int>>,
        height: Int
    ): List<Pair<Int, Int>> {

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

    // ========================================================================
    // FINGERTIP DETECTION
    // ========================================================================

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

    // ========================================================================
    // FINGERPRINT REGION
    // ========================================================================

    private fun calculateFingerprintRegion(
        fingerBand: RectF,
        fingertip: PointF,
        handType: HandType
    ): RectF {

        val fingerHeight = fingerBand.height()
        val roiLength = (fingerHeight * FINGERPRINT_ROI_LENGTH_MULTIPLIER).coerceAtLeast(1f)
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

    // ========================================================================
    // BITMAP CROP
    // ========================================================================

    private fun cropBitmap(
        bitmap: Bitmap,
        rect: RectF
    ): Bitmap {

        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmap.height)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    // ========================================================================
    // DIAGNOSTIC
    // ========================================================================

    private fun createDiagnosticImage(
        bitmap: Bitmap,
        fingers: List<FingerResult>,
        handType: HandType
    ): Bitmap {

        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val roiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }

        val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            style = Paint.Style.FILL
        }

        canvas.drawText(handType.name, 20f, 50f, textPaint)

        fingers.forEachIndexed { index, finger ->

            val color = when (index) {
                0 -> Color.RED
                1 -> Color.GREEN
                2 -> Color.BLUE
                else -> Color.YELLOW
            }

            bandPaint.color = color
            canvas.drawRect(finger.fingerBand, bandPaint)

            roiPaint.color = color
            canvas.drawRect(finger.fingerprintRegion, roiPaint)

            tipPaint.color = color
            canvas.drawCircle(finger.fingertip.point.x, finger.fingertip.point.y, 18f, tipPaint)

            canvas.drawText(
                "F${index + 1} tip=(${finger.fingertip.point.x.toInt()}, ${finger.fingertip.point.y.toInt()}) " +
                        "conf=%.2f".format(finger.fingertip.confidence),
                finger.fingerBand.left + 10f,
                finger.fingerBand.top + 42f,
                textPaint
            )
        }

        return output
    }
}