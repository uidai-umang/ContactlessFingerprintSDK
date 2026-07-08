package app.gov.uidai.capture.domain.method.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Size
import androidx.core.graphics.createBitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.scale
import androidx.core.graphics.toRectF
import app.gov.uidai.capture.domain.model.SegmentationResultData
import app.gov.uidai.capture.utils.extension.size
import app.gov.uidai.capture.utils.logExecutionTime
import kotlin.math.ceil
import kotlin.math.floor

class U2NetFloat32(context: Context, modelPath: String) {

    companion object {
        private val TAG = U2NetFloat32::class.simpleName
        private const val INPUT_IMAGE_SIZE = 320
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val NUM_CHANNELS = 3
    }

    private val interpreter: Interpreter
    private val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE) // reused pixel buffer
    private val imgData: ByteBuffer =
        ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(4) // tune for your device
        })
    }

    /**
     * Run inference from a Bitmap
     */
    fun run(bitmap: Bitmap): SegmentationResultData? {
        val scaled = if (bitmap.width != INPUT_IMAGE_SIZE || bitmap.height != INPUT_IMAGE_SIZE) {
            bitmap.scale(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)
        } else bitmap

        convertBitmapToBuffer(scaled)

        val output =
            Array(1) { Array(INPUT_IMAGE_SIZE) { Array(INPUT_IMAGE_SIZE) { FloatArray(1) } } }

        logExecutionTime(TAG, "Segmentation Inference Time") {
            interpreter.run(imgData, output)
        }

        val (mask, rect) = interpretResult(output, bitmap.size())

        return rect?.let {
            SegmentationResultData(
                box = it.toRectF(),
                mask = mask
            )
        }
    }

    private fun convertBitmapToBuffer(bitmap: Bitmap) {
        imgData.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixelIndex = 0
        for (y in 0 until INPUT_IMAGE_SIZE) {
            for (x in 0 until INPUT_IMAGE_SIZE) {
                val pixel = intValues[pixelIndex++]
                imgData.putFloat(((pixel shr 16 and 0xFF) / 255f)) // R
                imgData.putFloat(((pixel shr 8 and 0xFF) / 255f))  // G
                imgData.putFloat(((pixel and 0xFF) / 255f))        // B
            }
        }
    }

    private fun interpretResult(
        probMask: Array<Array<Array<FloatArray>>>,
        originalSize: Size
    ): Pair<Bitmap, Rect?> {
        val w = INPUT_IMAGE_SIZE
        val h = INPUT_IMAGE_SIZE

        val maskSmall = createBitmap(w, h)
        val pixels = IntArray(w * h)

        // Binary mask: 1 = finger, 0 = background
        val binary = Array(h) { BooleanArray(w) }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = probMask[0][y][x][0]
                if (p > CONFIDENCE_THRESHOLD) {
                    binary[y][x] = true
                    pixels[y * w + x] = Color.WHITE
                } else {
                    pixels[y * w + x] = Color.BLACK
                }
            }
        }
        maskSmall.setPixels(pixels, 0, w, 0, 0, w, h)

        // Connected component labeling (4-neighbor flood fill)
        data class Blob(var minX: Int, var minY: Int, var maxX: Int, var maxY: Int, var area: Int)

        val visited = Array(h) { BooleanArray(w) }
        val blobs = mutableListOf<Blob>()

        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        fun floodFill(sx: Int, sy: Int): Blob {
            val q = ArrayDeque<Pair<Int, Int>>()
            q.add(sx to sy)
            visited[sy][sx] = true
            var minX = sx
            var minY = sy
            var maxX = sx
            var maxY = sy
            var area = 0

            while (q.isNotEmpty()) {
                val (x, y) = q.removeFirst()
                area++
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y

                for (k in 0..3) {
                    val nx = x + dx[k]
                    val ny = y + dy[k]
                    if (nx in 0 until w && ny in 0 until h &&
                        binary[ny][nx] && !visited[ny][nx]
                    ) {
                        visited[ny][nx] = true
                        q.add(nx to ny)
                    }
                }
            }
            return Blob(minX, minY, maxX, maxY, area)
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (binary[y][x] && !visited[y][x]) {
                    blobs.add(floodFill(x, y))
                }
            }
        }

        if (blobs.isEmpty()) return maskSmall to null

        // Pick largest blob
        val best = blobs.maxByOrNull { it.area }!!

        // Scale bounding box to original size
        val scaleX = originalSize.width.toFloat() / w
        val scaleY = originalSize.height.toFloat() / h

        val left = floor(best.minX * scaleX).toInt().coerceIn(0, originalSize.width)
        val top = floor(best.minY * scaleY).toInt().coerceIn(0, originalSize.height)
        val right = ceil((best.maxX + 1) * scaleX).toInt().coerceIn(left, originalSize.width)
        val bottom = ceil((best.maxY + 1) * scaleY).toInt().coerceIn(top, originalSize.height)
        val rect = Rect(left, top, right, bottom)

        // Upscale mask to original size
        val maskFull = maskSmall.scale(originalSize.width, originalSize.height)

        // Crop mask to bounding box
        val croppedMask =
            Bitmap.createBitmap(maskFull, rect.left, rect.top, rect.width(), rect.height())

        return croppedMask to rect
    }

    fun close() {
        interpreter.close()
    }
}