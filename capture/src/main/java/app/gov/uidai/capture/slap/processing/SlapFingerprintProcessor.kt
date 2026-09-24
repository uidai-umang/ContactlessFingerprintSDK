package app.gov.uidai.capture.slap.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.gov.uidai.capture.domain.method.final_processing.FinalProcessingU2Net
import app.gov.uidai.capture.domain.method.segmentation.U2NetFloat32

class SlapFingerprintProcessor(
    private val context: Context
) {

    companion object {
        private const val TAG = "SlapFingerprintProcessor"
        private const val U2NET_MODEL_ASSET_PATH = "u2net_320x320_float32.tflite"
    }

    private val u2Net: U2NetFloat32 by lazy {
        U2NetFloat32(context, U2NET_MODEL_ASSET_PATH)
    }

    private val handRegionDetector = SlapHandRegionDetector()
    private val bandDetector = SlapFingerBandDetector()

    enum class HandType { LEFT, RIGHT }

    private fun HandType.toDetectorHandType(): SlapFingerBandDetector.HandType = when (this) {
        HandType.LEFT -> SlapFingerBandDetector.HandType.LEFT
        HandType.RIGHT -> SlapFingerBandDetector.HandType.RIGHT
    }

    data class FingerResult(
        val index: Int,
        val fingerBand: RectF,
        val fingertip: SlapFingerBandDetector.Fingertip,
        val fingerprintRegion: RectF,
        val fingerprintRoi: Bitmap,
        val segmentationMask: Bitmap?,
        val ridgeImage: Bitmap
    )

    data class Result(
        val handRegion: RectF?,
        val handRegionBitmap: Bitmap,
        val fingers: List<FingerResult>,
        val diagnosticImage: Bitmap
    )

    fun process(bitmap: Bitmap, handType: HandType): Result {

        require(bitmap.width > 0 && bitmap.height > 0)

        val handDetection = handRegionDetector.detect(bitmap)

        val handRegionBitmap = handDetection?.handRegion?.let { cropBitmap(bitmap, it) } ?: bitmap

        val fingers = if (handDetection != null) {
            val bandResult = bandDetector.detectFromBinaryMask(
                binary = handDetection.binaryMask,
                width = handDetection.maskWidth,
                height = handDetection.maskHeight,
                handType = handType.toDetectorHandType(),
                invScale = handDetection.invScale
            )

            bandResult.bands.mapIndexed { index, band ->

                val roi = cropBitmap(bitmap, band.fingerprintRegion)

                val segResult = try {
                    u2Net.run(roi)
                } catch (e: Exception) {
                    null
                }

                val (refinedRoi, mask) = if (segResult != null) {
                    cropBitmap(roi, segResult.box) to segResult.mask
                } else {
                    roi to null
                }

                val ridgeImage = FinalProcessingU2Net.run(refinedRoi, mask)

                FingerResult(
                    index = index,
                    fingerBand = band.fingerBand,
                    fingertip = band.fingertip,
                    fingerprintRegion = band.fingerprintRegion,
                    fingerprintRoi = roi,
                    segmentationMask = mask,
                    ridgeImage = ridgeImage
                )
            }
        } else {
            emptyList()
        }

        val diagnostic = createDiagnosticImage(bitmap, handDetection?.handRegion, fingers, handType)

        return Result(
            handRegion = handDetection?.handRegion,
            handRegionBitmap = handRegionBitmap,
            fingers = fingers,
            diagnosticImage = diagnostic
        )
    }

    private fun cropBitmap(bitmap: Bitmap, rect: RectF): Bitmap {
        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun createDiagnosticImage(
        bitmap: Bitmap,
        handRegion: RectF?,
        fingers: List<FingerResult>,
        handType: HandType
    ): Bitmap {

        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val handRegionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.WHITE
        }

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

        handRegion?.let { canvas.drawRect(it, handRegionPaint) }

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