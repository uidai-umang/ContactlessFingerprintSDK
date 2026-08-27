package app.gov.uidai.capture.domain.method.blur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.gov.uidai.capture.utils.logExecutionTime
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DensenetBlur(
    private val context: Context,
    private val modelPath: String
) {
    private var interpreter: Interpreter? = null

    // Initialize image processor
    private val imageProcessor: ImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(INPUT_IMAGE_HEIGHT, INPUT_IMAGE_WIDTH, ResizeOp.ResizeMethod.BILINEAR))
            .build()
    }

    companion object {
        private val TAG = DensenetBlur::class.simpleName
        private const val INPUT_IMAGE_WIDTH = 224
        private const val INPUT_IMAGE_HEIGHT = 224
    }

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        try {
            val options = Interpreter.Options().apply {
                /*numThreads = 4
                Interpreter.Options.setUseNNAPI = true // Use Android Neural Networks API if available*/
            }

            val model = FileUtil.loadMappedFile(context, modelPath)
            interpreter = Interpreter(model, options)

            Log.d(TAG, "Blur Model loaded successfully")
            Log.d(TAG, "Input shape: ${interpreter?.getInputTensor(0)?.shape()?.contentToString()}")
            Log.d(TAG, "Output shape: ${interpreter?.getOutputTensor(0)?.shape()?.contentToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Blur interpreter", e)
        }
    }

    /**
     * Detect blur from bitmap image
     */
    @SuppressLint("DefaultLocale")
    fun detectBlur(bitmap: Bitmap, blurThreshold: Float): BlurResult? {
        return try {
            // Preprocess Image
            val inputBuffer = preprocessImage(bitmap)

            // Run Model
            val outputBuffer = runInference(inputBuffer)

            // Postprocess output
            return interpretResult(outputBuffer, blurThreshold)
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            null
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer{
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // Prepare input buffer
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Fill input buffer with normalized pixel values
        val intValues = IntArray(INPUT_IMAGE_WIDTH * INPUT_IMAGE_HEIGHT)
        processedImage.bitmap.getPixels(intValues, 0, INPUT_IMAGE_WIDTH, 0, 0, INPUT_IMAGE_WIDTH, INPUT_IMAGE_HEIGHT)

        inputBuffer.rewind()
        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((pixelValue and 0xFF) / 255.0f))
        }
        return inputBuffer
    }

    private fun runInference(inputBuffer: ByteBuffer): ByteBuffer {
        // Prepare output buffer
        val outputBuffer = ByteBuffer.allocateDirect(4)
        outputBuffer.order(ByteOrder.nativeOrder())

        // Run inference
        logExecutionTime(TAG, "Blur Inference Time") {
            interpreter?.run(inputBuffer, outputBuffer)
        }

        // Get prediction
        outputBuffer.rewind()

        return outputBuffer
    }

    @SuppressLint("DefaultLocale")
    private fun interpretResult(result: ByteBuffer, blurThreshold: Float): BlurResult {
        val prediction = result.float

        val isSharp = prediction >= blurThreshold

        Log.d(TAG, "Prediction of Blur model: $prediction , Threshold: $blurThreshold")

        return BlurResult(isSharp, prediction)
    }

    /**
     * Close the interpreter and release resources
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }

    data class BlurResult(
        val isSharp: Boolean,
        val confidence: Float
    )
}