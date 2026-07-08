package app.gov.uidai.capture.domain.method.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.scale
import app.gov.uidai.capture.domain.model.SegmentationResultData
import app.gov.uidai.capture.utils.logExecutionTime
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp


class YOLOAnalyser(context: Context, modelPath: String) {
    companion object {
        private val TAG = YOLOAnalyser::class.simpleName
        private const val INPUT_SIZE = 800
    }

    private var interpreter: Interpreter? = null
    private val imageProcessor: ImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .build()
    }

    init {
        try {
            val options = Interpreter.Options().apply {
                // numThreads = 4
            }

            val model = FileUtil.loadMappedFile(context, modelPath)
            interpreter = Interpreter(model, options)

            Log.d(TAG, "Model loaded successfully")
            Log.d(TAG, "Input shape: ${interpreter?.getInputTensor(0)?.shape()?.contentToString()}")
            Log.d(TAG, "Output shape: ${interpreter?.getOutputTensor(0)?.shape()?.contentToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing interpreter", e)
        }
    }

    fun analyse(bitmap: Bitmap, confidenceThreshold: Float) : SegmentationResultData? {
        val bitmapHeight = bitmap.height
        val bitmapWidth = bitmap.width

        val resizedBitmap = bitmap.scale(INPUT_SIZE, INPUT_SIZE)

        val inputBuffer = preprocess(resizedBitmap)
        val outputBuffer = Array(1) { Array(8) { FloatArray(13125) } }  // Changed to match model output

        logExecutionTime(TAG, "Segmentation Inference Time") {
            interpreter?.run(inputBuffer, outputBuffer)
        }

        val results = interpretResults(bitmapHeight, bitmapWidth, outputBuffer[0], confidenceThreshold)
        Log.d(TAG, "Segmentation result size: ${results.size}")
        return results.firstOrNull()
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixelValue in intValues) {
            val r = (pixelValue shr 16 and 0xFF) / 255.0f
            val g = (pixelValue shr 8 and 0xFF) / 255.0f
            val b = (pixelValue and 0xFF) / 255.0f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        return inputBuffer
    }

    private fun interpretResults(a: Int, b: Int, output: Array<FloatArray>, confidenceThreshold: Float): List<SegmentationResultData> {
        val results = mutableListOf<SegmentationResultData>()

        Log.d(TAG, "Output size: ${output.size}")
        Log.d(TAG, "Rows: ${output[0].size}")

        val numBoxes = output[0].size

        for (i in 0 until numBoxes) {
            val confidence = output[6][i]

            if (confidence > confidenceThreshold) {
                val x = output[0][i]
                val y = output[1][i]
                val w = output[2][i]
                val h = output[3][i]

                val left = (x - w / 2) * b
                val top = (y - h / 2) * a
                val right = (x + w / 2) * b
                val bottom = (y + h / 2) * a

                results.add(
                    SegmentationResultData(
                        box = RectF(left, top, right, bottom),
                        label = "Fingerprint",
                        confidence = confidence
                    )
                )
            }
        }

        return nms(results, 0.3f) // iouThreshold
    }

    private fun sigmoid(x: Float): Float {
        return 1.0f / (1.0f + exp(-x))
    }

    private fun nms(boxes: List<SegmentationResultData>, iouThreshold: Float): List<SegmentationResultData> {
        val sortedBoxes = boxes.sortedByDescending { it.confidence }
        val selectedBoxes = mutableListOf<SegmentationResultData>()

        for (box in sortedBoxes) {
            var shouldSelect = true
            for (selectedBox in selectedBoxes) {
                if (calculateIoU(box.box, selectedBox.box) > iouThreshold) {
                    shouldSelect = false
                    break
                }
            }
            if (shouldSelect) selectedBoxes.add(box)
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionArea = RectF().apply {
            setIntersect(box1, box2)
        }.let { if (it.isEmpty) 0f else it.width() * it.height() }

        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        return intersectionArea / unionArea
    }

    private fun drawDetectionResult(bitmap: Bitmap, results: List<SegmentationResultData>): List<Bitmap> {
        val croppedBitmaps = mutableListOf<Bitmap>()

        for (result in results) {
            val boundingBox = result.box
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                boundingBox.left.toInt(),
                boundingBox.top.toInt(),
                boundingBox.width().toInt(),
                boundingBox.height().toInt()
            )
            croppedBitmaps.add(croppedBitmap)
            break
        }

        return croppedBitmaps
    }
}