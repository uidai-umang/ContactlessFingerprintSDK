package app.gov.uidai.capture.slap.processing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Classical (no ML, no palm needed) per-finger band + fingertip + ROI
 * detector.
 *
 * Pulled OUT of SlapFingerprintProcessor, so it can be shared by:
 *  - SlapFingerprintProcessor (capture-time): bands -> ROI -> U2Net -> ridge
 *  - the live camera loop (SlapFrameAnalyzer): bands + ROI only, for the
 *    capture gate and the on-screen per-finger boxes
 *
 * One implementation, two callers -- so live and capture-time detection
 * can't quietly drift apart. Never uses MediaPipe/hand-landmarks: this is
 * exactly why it doesn't need the palm in frame, only the finger crop.
 *
 * Skin classification is done by YCbCr CHROMINANCE, not luminance. An
 * earlier version grayscaled the frame and ran Otsu thresholding, with a
 * heuristic that sampled the image border to guess whether the "finger"
 * was the darker or brighter cluster. That guess flipped between frames
 * whenever a finger sat near the frame edge or a background object was
 * itself dark, and worse, a dark BACKGROUND object is indistinguishable
 * from a dark SKIN region under luminance alone -- there is no signal to
 * tell them apart. Chrominance fixes both: skin color sits in a fairly
 * narrow, lighting-independent Cb/Cr band, and a dark (or light) neutral
 * object has near-zero color saturation regardless of brightness, so it
 * falls outside that band no matter how dark it is.
 */
class SlapFingerBandDetector {

    companion object {
        private val TAG = SlapFingerBandDetector::class.simpleName
        const val FINGER_COUNT = 4

        private const val ROW_SMOOTHING_RADIUS = 8
        private const val MIN_FINGER_HEIGHT_RATIO = 0.04f

        // Was 0.30 -- that let a single accepted band be up to 30% of the
        // analysis height (170px at 568px height). Four fingers stacked
        // with gaps can't each realistically be 30% of the frame, so a
        // band anywhere near that size is almost certainly a merged false
        // region (e.g. a background object), not a real finger. Tightened
        // to bound how large a single false band -- and therefore its
        // derived fingerprint ROI -- can get.
        private const val MAX_FINGER_HEIGHT_RATIO = 0.20f

        // A candidate band must average at least this fraction of the
        // image's WIDTH in skin pixels to count as a real finger. This is
        // an ABSOLUTE floor, unlike the peak-relative activeThreshold
        // below. Was 0.35 (112px at 320px analysis width) -- that's a
        // FIXED pixel-width floor regardless of how far the finger is
        // from the camera, so a finger farther away (occupying fewer
        // columns) was rejected outright even though it was the only real
        // skin region in frame. Lowered since chrominance classification
        // is inherently much cleaner than luminance Otsu was, so this
        // floor is now just a backstop against tiny noise, not the
        // primary filter.
        private const val MIN_BAND_FILL_RATIO = 0.12f

        // Relative to this frame's own peak row-projection value.
        private const val RELATIVE_ACTIVE_THRESHOLD_RATIO = 0.20

        // We don't scan the entire band for the fingertip boundary -- just
        // the middle portion, where it's cleanest.
        private const val TIP_SCAN_TOP_RATIO = 0.20f
        private const val TIP_SCAN_BOTTOM_RATIO = 0.80f

        /*
         * Fingerprint ROI (tip-anchored box, not the full-width row band).
         * The fingerprint area is BEHIND the fingertip, not the fingertip
         * point itself. Length is expressed relative to finger thickness.
         */
        private const val FINGERPRINT_ROI_LENGTH_MULTIPLIER = 2.2f
        private const val FINGERPRINT_ROI_VERTICAL_PADDING_RATIO = 0.08f

        // YCbCr skin-tone chrominance range. Deliberately wide to cover a
        // broad range of skin tones (this SDK serves a large, diverse
        // population) -- if testing shows darker skin tones still get
        // missed, widen CR_MIN/CB_MIN further; if pale background objects
        // (wood, some fabrics, skin-toned walls/surfaces) start getting
        // picked up as false positives, narrow the range back down. Watch
        // the "skin mask" log line below while tuning.
        private const val CB_MIN = 70
        private const val CB_MAX = 135
        private const val CR_MIN = 125
        private const val CR_MAX = 180
    }

    enum class HandType {
        LEFT,
        RIGHT
    }

    data class Fingertip(
        val point: PointF,
        val confidence: Float
    )

    data class BandResult(
        // Full-width horizontal strip this finger was detected in --
        // useful for diagnostics, NOT what should be drawn/cropped as
        // "the finger" (see fingerprintRegion).
        val fingerBand: RectF,
        val fingertip: Fingertip,
        // Tip-anchored box actually covering the fingerprint-bearing area.
        // This is what the live overlay and capture-time ROI crop should
        // both use.
        val fingerprintRegion: RectF
    )

    data class DetectionResult(
        val bands: List<BandResult>,
        // Skin binary mask + the (possibly downscaled) dimensions it was
        // computed at -- callers that need pixel-level work (e.g. cropping
        // an ROI) should re-derive from the bitmap they passed in; this is
        // exposed mainly for diagnostics.
        val analysisWidth: Int,
        val analysisHeight: Int
    )

    /**
     * Detects up to FINGER_COUNT horizontal finger bands + a fingertip +
     * fingerprint ROI per band. Everything is always returned in
     * [bitmap]'s OWN (full-resolution) coordinate space, even when
     * [maxAnalysisWidth] causes internal downscaling for speed -- callers
     * never need to know that happened.
     *
     * [maxAnalysisWidth]: if set and smaller than bitmap.width, detection
     * runs on a downscaled copy for speed (the live loop needs this; a
     * single capture-time call on a still frame does not).
     */
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

        if (analysisBitmap !== bitmap) {
            analysisBitmap.recycle()
        }

        Log.d(
            TAG,
            "detect(): ${bands.size}/${FINGER_COUNT} finger bands found " +
                    "(analysis ${width}x${height})"
        )

        // Scale bands/fingertips/ROI back up to the caller's original
        // bitmap coordinates -- callers should never have to know
        // downscaling happened internally.
        val invScale = 1f / scale
        val scaledBands = if (scale == 1f) {
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

    private fun RectF.scaledBy(factor: Float): RectF =
        RectF(left * factor, top * factor, right * factor, bottom * factor)

    // ========================================================================
    // CHROMA EXTRACTION + SKIN CLASSIFICATION
    // ========================================================================

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

    // ========================================================================
    // GAUSSIAN BLUR (generic over any single-channel plane -- used here on
    // Cb/Cr instead of grayscale luminance)
    // ========================================================================

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

    // ========================================================================
    // FINGER BANDS
    // ========================================================================

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
                } else if (bandHeight in minHeight..maxHeight) {
                    Log.d(
                        TAG,
                        "rejected band [$start,$end]: score=%.1f below absolute floor %.1f (width=$width)"
                            .format(bandScore, minAbsoluteFill)
                    )
                } else if (bandHeight > maxHeight) {
                    Log.d(
                        TAG,
                        "rejected band [$start,$end]: height=$bandHeight exceeds max $maxHeight -- likely a merged false region"
                    )
                }
                start = -1
            }
        }

        val merged = mergeNearbyBands(candidates, height)

        // Always re-score and take the top FINGER_COUNT, even when there
        // are 4 or fewer merged candidates -- previously a lone noise
        // band sailed through untouched whenever total count was already
        // <= FINGER_COUNT, since scoring only ran in the "too many"
        // branch. The absolute floor above already screens most noise,
        // this is a second pass that also handles ties/near-misses.
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
    // FINGERPRINT ROI (moved from SlapFingerprintProcessor -- now shared)
    // ========================================================================

    private fun calculateFingerprintRegion(
        fingerBand: RectF,
        fingertip: PointF,
        handType: HandType
    ): RectF {

        val fingerHeight = fingerBand.height()
        val roiLength = (fingerHeight * FINGERPRINT_ROI_LENGTH_MULTIPLIER)
            .coerceAtLeast(1f)
            // Safety cap independent of how fingerHeight was computed --
            // even if a bad band slips through the height/fill filters
            // above, this stops its derived ROI from ballooning past half
            // the frame width (the "whole dark object becomes the box"
            // failure mode).
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