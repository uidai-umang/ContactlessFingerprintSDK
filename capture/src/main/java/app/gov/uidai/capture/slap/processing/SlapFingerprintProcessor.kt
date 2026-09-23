package app.gov.uidai.capture.slap.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.gov.uidai.capture.domain.method.final_processing.FinalProcessingU2Net
import app.gov.uidai.capture.domain.method.segmentation.U2NetFloat32

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

        // Reuses the SAME model weights single capture uses -- no
        // slap-trained model exists. Whether these weights, presumably
        // trained on single-finger framing, generalise to a ROI cropped
        // FROM a slap image is UNVERIFIED -- test on real captures.
        private const val U2NET_MODEL_ASSET_PATH = "u2net_320x320_float32.tflite"
    }

    // Lazy so the TFLite interpreter only loads once this processor is
    // actually used.
    private val u2Net: U2NetFloat32 by lazy {
        U2NetFloat32(context, U2NET_MODEL_ASSET_PATH)
    }

    // Band + fingertip detection (Otsu + row projection) now lives in
    // SlapFingerBandDetector, shared with the live camera loop -- see that
    // file's kdoc. This class only adds what's capture-specific: ROI crop,
    // U2Net segmentation, ridge extraction.
    private val bandDetector = SlapFingerBandDetector()

    enum class HandType {
        LEFT,
        RIGHT
    }

    private fun HandType.toDetectorHandType(): SlapFingerBandDetector.HandType = when (this) {
        HandType.LEFT -> SlapFingerBandDetector.HandType.LEFT
        HandType.RIGHT -> SlapFingerBandDetector.HandType.RIGHT
    }

    data class FingerResult(
        val index: Int,

        /*
         * Original horizontal finger band.
         */
        val fingerBand: RectF,

        /*
         * Detected fingertip.
         */
        val fingertip: SlapFingerBandDetector.Fingertip,

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

        val detection = bandDetector.detect(
            bitmap = bitmap,
            handType = handType.toDetectorHandType()
            // No maxAnalysisWidth -- this runs once on the captured still,
            // full resolution is affordable here (unlike the live loop).
        )

        val fingers = detection.bands.mapIndexed { index, bandResult ->

            val band = bandResult.fingerBand
            val fingertip = bandResult.fingertip
            // Now computed once in SlapFingerBandDetector and shared with
            // the live loop -- see that class's kdoc.
            val fingerprintRegion = bandResult.fingerprintRegion

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