package app.gov.uidai.capture.domain.method.finger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.Size
import androidx.core.graphics.scale
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import app.gov.uidai.capture.utils.logExecutionTime
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.createBitmap

class MediapipeSegmenter(
    val context: Context,
    val modelPath: String,
    val fingerCategoryId: List<UInt>
) {
    // For this example this needs to be a var so it can be reset on changes. If the ImageSegmenter
    // will not change, a lazy val would be preferable.
    private var imageSegmenter: ImageSegmenter? = null

    companion object {
        private val TAG = MediapipeSegmenter::class.simpleName
    }

    init {
        setupImageSegmenter()
    }

    // Segmenter must be closed when creating a new one to avoid returning results to a
    // non-existent object
    fun clearImageSegmenter() {
        imageSegmenter?.close()
        imageSegmenter = null
    }

    // Return running status of image segmenter helper
    fun isClosed(): Boolean {
        return imageSegmenter == null
    }

    // Initialize the image segmenter using current settings on the
    // thread that is using it. CPU can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // segmenter
    private fun setupImageSegmenter() {
        try {
            val baseOptionsBuilder = BaseOptions.builder().apply {
                setDelegate(Delegate.CPU)
                setModelAssetPath(modelPath)
            }
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = ImageSegmenter.ImageSegmenterOptions.builder().apply {
                setRunningMode(RunningMode.IMAGE)
                setBaseOptions(baseOptions)
                setOutputCategoryMask(true)
                setOutputConfidenceMasks(false)
            }
            val options = optionsBuilder.build()
            imageSegmenter = ImageSegmenter.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            Log.e(
                TAG,
                "Image segmenter failed to load model with error: " + e.message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            Log.e(
                TAG,
                "Image segmenter failed to load model with error: " + e.message
            )
        }
    }

    fun segmentLiveStreamFrame(bitmap: Bitmap, cutoutRect: RectF, previewSize: Size): ResultBundle {
        val originalW = bitmap.width
        val originalH = bitmap.height
        val scaledW = previewSize.width
        val scaledH = previewSize.height

        val scaleX = scaledW.toFloat() / originalW
        val scaleY = scaledH.toFloat() / originalH

        val scaledBitmap = bitmap.scale(previewSize.width, previewSize.height)

        Log.d(TAG, "ScaleX: $scaleX, ScaleY: $scaleY")

        val scaledCutoutRect = Rect(
            (cutoutRect.left * scaleX).toInt().coerceIn(0, scaledW),
            (cutoutRect.top * scaleY).toInt().coerceIn(0, scaledH),
            (cutoutRect.right * scaleX).toInt().coerceIn(0, scaledW),
            (cutoutRect.bottom * scaleY).toInt().coerceIn(0, scaledH)
        )

        val mpImage = BitmapImageBuilder(scaledBitmap).build()
        val result = logExecutionTime(TAG, "Finger Check Inference Time") {
            imageSegmenter?.segment(mpImage)
        }

        val outputMPImage = result?.categoryMask()?.get()
        val byteBuffer = ByteBufferExtractor.extract(outputMPImage)

        // Crop the mask buffer
        val croppedBuffer = cropByteBuffer(
            src = byteBuffer,
            srcWidth = scaledW,
            srcHeight = scaledH,
            cropRect = scaledCutoutRect
        )

        // Run interpretation and mask creation on cropped buffer
        var (box, isValid) = logExecutionTime(TAG, "InterpretResult") {
            interpretResult(
                byteBuffer = croppedBuffer, // duplicate so interpretResult can rewind
                width = scaledCutoutRect.width(),
                height = scaledCutoutRect.height()
            )
        }

        box?.let{
            val boxInScaledBitmap = RectF(
                (it.left + scaledCutoutRect.left).toFloat(),
                (it.top + scaledCutoutRect.top).toFloat(),
                (it.right + scaledCutoutRect.left).toFloat(),
                (it.bottom + scaledCutoutRect.top).toFloat()
            )

            box = Rect(
                (boxInScaledBitmap.left / scaleX).toInt().coerceIn(0, originalW),
                (boxInScaledBitmap.top / scaleY).toInt().coerceIn(0, originalH),
                (boxInScaledBitmap.right / scaleX).toInt().coerceIn(0, originalW),
                (boxInScaledBitmap.bottom / scaleY).toInt().coerceIn(0, originalH)
            )
        }

        val mask = logExecutionTime(TAG, "Get Bitmap Mask") {
            createFingerMaskBitmap(byteBuffer, scaledW, scaledH)
        }

        return ResultBundle(
            isValid = isValid,
            box = box,
            mask = mask
        )
    }

    private fun cropByteBuffer(
        src: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        cropRect: Rect
    ): ByteBuffer {
        require(
            cropRect.left >= 0 && cropRect.top >= 0 &&
                    cropRect.right <= srcWidth && cropRect.bottom <= srcHeight
        ) {
            "Crop rect out of bounds"
        }

        val cropWidth = cropRect.width()
        val cropHeight = cropRect.height()
        val dst = ByteBuffer.allocateDirect(cropWidth * cropHeight)

        val rowBuffer = ByteArray(cropWidth) // reuse for all rows
        src.rewind()

        for (y in cropRect.top until cropRect.bottom) {
            val rowStart = y * srcWidth + cropRect.left
            src.position(rowStart)
            src.get(rowBuffer, 0, cropWidth)
            dst.put(rowBuffer)
        }

        dst.rewind()
        return dst
    }

    private fun createFingerMaskBitmap(
        byteBuffer: ByteBuffer,
        width: Int,
        height: Int
    ): Bitmap {
        val overlay = createBitmap(width, height)

        val pixels = IntArray(width * height)
        byteBuffer.rewind()

        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelValue = byteBuffer.get().toUInt()
                if (pixelValue in fingerCategoryId) {
                    // Finger → transparent
                    pixels[i] = 0x00000000
                } else {
                    // Background → semi‑transparent black
                    pixels[i] = 0x44000000  // alpha=0x88 (~53%), RGB=black
                }
                i++
            }
        }

        overlay.setPixels(pixels, 0, width, 0, 0, width, height)
        return overlay
    }

    private fun interpretResult(
        byteBuffer: ByteBuffer,
        width: Int,
        height: Int
    ): Pair<Rect?, Boolean> {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var skinPixelCount = 0

        byteBuffer.rewind()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelValue = byteBuffer.get().toUInt()
                if (pixelValue in fingerCategoryId) {
                    skinPixelCount++
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }
        byteBuffer.rewind()

        val boundingBox = if (skinPixelCount > 0) Rect(minX, minY, maxX, maxY) else null
        var isResultValid = true

        /*if (boundingBox != null) {
            val boundingBoxArea = boundingBox.width() * boundingBox.height()
            val totalImageArea = width * height

            val areaRatio = skinPixelCount.toFloat() / totalImageArea
            val fillFactor = if (boundingBoxArea > 0) skinPixelCount.toFloat() / boundingBoxArea else 0f
            val aspectRatio = if (boundingBox.height() > 0) boundingBox.width().toFloat() / boundingBox.height() else 0f

            val isAreaValid = areaRatio > 0.1 && areaRatio < 0.9
            val isFillFactorValid = fillFactor > 0.3
            val isAspectRatioValid = aspectRatio > 0.5 && aspectRatio < 2.0

            isResultValid = isAreaValid && isFillFactorValid && isAspectRatioValid
        }*/

        return boundingBox to isResultValid
    }

    fun getBitmapMask(
        byteBuffer: ByteBuffer,
        outputWidth: Int,
        outputHeight: Int
    ): Bitmap {
        // Create the mask bitmap with colors and the set of detected labels.
        val pixels = IntArray(byteBuffer.capacity())
        for (i in pixels.indices) {
            // Using unsigned int here because selfie segmentation returns 0 or 255U (-1 signed)
            // with 0 being the found person, 255U for no label.
            // Deeplab uses 0 for background and other labels are 1-19,
            // so only providing 20 colors from ImageSegmenterHelper -> labelColors
            val index = byteBuffer.get(i).toUInt() % 20U
            val color = if (index in fingerCategoryId) Color.BLUE else Color.BLACK
            pixels[i] = color
        }

        return Bitmap.createBitmap(
            pixels,
            outputWidth,
            outputHeight,
            Bitmap.Config.ARGB_8888
        )
    }

    // Wraps results from inference, the time it takes for inference to be
    // performed.
    data class ResultBundle(
        val isValid: Boolean,
        val box: Rect?,
        val mask: Bitmap?
    )
}
