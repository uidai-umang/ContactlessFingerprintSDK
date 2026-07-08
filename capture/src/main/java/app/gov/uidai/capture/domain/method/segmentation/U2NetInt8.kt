package app.gov.uidai.capture.domain.method.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Size
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.graphics.toRectF
import app.gov.uidai.capture.domain.model.SegmentationResultData
import app.gov.uidai.capture.utils.extension.size
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor

class U2NetInt8(context: Context, modelPath: String) {
    companion object {
        private const val INPUT_IMAGE_SIZE = 224
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val NUM_CHANNELS = 3
    }

    private var interpreter: Interpreter? = null

    private val imageProcessor: ImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .build()
    }

    init {
        try {
            val options = Interpreter.Options().apply {
                // numThreads = 4
            }
            val model = FileUtil.loadMappedFile(context, modelPath)
            interpreter = Interpreter(model, options)
        } catch (e: Exception) {

        }
    }

    fun analyse(bitmap: Bitmap): SegmentationResultData? {
        return try {
            // Preprocess Image
            val inputBuffer = preprocessImage(bitmap)

            // Run Model
            val outputBuffer = runInference(inputBuffer)

            val (mask, rect) = interpretResult(outputBuffer, bitmap.size())

            return rect?.let {
                SegmentationResultData(
                    box = it.toRectF(),
                    mask = mask
                )
            }
        } catch (e: Exception) {
            null
        }
    }


    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val scaled = bitmap.scale(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)

        val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
        scaled.getPixels(intValues, 0, INPUT_IMAGE_SIZE, 0, 0, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)

        val buffer = ByteBuffer.allocateDirect(
            INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * NUM_CHANNELS
        ).apply {
            order(ByteOrder.nativeOrder())
        }

        // Quantized symmetric/asymmetric int8
        for (p in intValues) {
            buffer.put(((p shr 16) and 0xFF).toByte())
            buffer.put(((p shr 8) and 0xFF).toByte())
            buffer.put((p and 0xFF).toByte())
        }
        buffer.rewind()
        return buffer
    }

    private fun runInference(input: ByteBuffer): ByteBuffer {
        val tflite = requireNotNull(interpreter) { "Interpreter not initialized" }

        val output = ByteBuffer.allocateDirect(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE).apply {
            order(ByteOrder.nativeOrder())
        }
        output.rewind()

        // Dequantize to float prob
        return output
    }

    private fun interpretResult(
        probMask: ByteBuffer,
        originalSize: Size
    ): Pair<Bitmap, Rect?> {
        val buf = probMask.duplicate()
        buf.rewind()

        val w = INPUT_IMAGE_SIZE
        val h = INPUT_IMAGE_SIZE

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        val mask = createBitmap(w, h)
        val pixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = x * h + y
                val uint8 = buf.get().toInt() and 0xFF
                val p = uint8 / 255.0f

                val maskValue = if (p > CONFIDENCE_THRESHOLD) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                    255
                } else {
                    0
                }

                pixels[i] = Color.rgb(maskValue, maskValue, maskValue)
            }
        }

        mask.setPixels(pixels, 0, INPUT_IMAGE_SIZE, 0, 0, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)
        // Log.d(TAG, "Mask: $mask")

        // No foreground at the chosen threshold.
        if (maxX < 0 || maxY < 0) return mask to null

        val scaleX = originalSize.width.toFloat() / w
        val scaleY = originalSize.height.toFloat() / h

        // Rect in Android uses [left, top, right, bottom) (right/bottom are exclusive).
        val left = floor(minX * scaleX).toInt().coerceIn(0, originalSize.width)
        val top = floor(minY * scaleY).toInt().coerceIn(0, originalSize.height)
        val right = ceil((maxX + 1) * scaleX).toInt().coerceIn(left, originalSize.width)
        val bottom = ceil((maxY + 1) * scaleY).toInt().coerceIn(top, originalSize.height)

        return mask to Rect(left, top, right, bottom)
    }

    // Optional: Create colored overlay for visualization (like in your testing)
    fun createColoredOverlay(originalBitmap: Bitmap, maskBitmap: Bitmap): Bitmap {
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // Scale mask to original image size if needed
        val scaledMask = if (maskBitmap.width != originalBitmap.width ||
            maskBitmap.height != originalBitmap.height) {
            maskBitmap.scale(originalBitmap.width, originalBitmap.height)
        } else {
            maskBitmap
        }

        val paint = Paint().apply {
            color = Color.argb(128, 255, 0, 0) // Semi-transparent red overlay
            isAntiAlias = true
        }

        // Apply overlay where mask indicates fingertip (white pixels)
        for (x in 0 until scaledMask.width) {
            for (y in 0 until scaledMask.height) {
                val pixel = scaledMask[x, y]
                val gray = Color.red(pixel) // Since it's grayscale, R=G=B

                if (gray > 128) { // If mask indicates fingertip
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }
        }

        return resultBitmap
    }
}