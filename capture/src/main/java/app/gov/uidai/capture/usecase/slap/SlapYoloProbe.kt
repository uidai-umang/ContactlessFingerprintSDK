package app.gov.uidai.capture.usecase.slap

import android.content.Context
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SlapYoloProbe(context: Context) {

    companion object {
        private const val TAG = "SlapYoloProbe"
        private const val MODEL = "best_float32.tflite"
        private const val INPUT = 800
        private const val NUM_BOXES = 13125
        private const val CONF = 0.25f
        private const val NMS_IOU = 0.3f
    }

    private class Candidate(val score: Float, val l: Float, val t: Float, val r: Float, val b: Float)

    private val interpreter: Interpreter = createInterpreter(context)
    private val inputBuffer = ByteBuffer.allocateDirect(INPUT * INPUT * 3 * 4).order(ByteOrder.nativeOrder())
    private val inputFloats = inputBuffer.asFloatBuffer()
    private val output = Array(1) { Array(8) { FloatArray(NUM_BOXES) } }
    private val xMap = IntArray(INPUT)
    private val yMap = IntArray(INPUT)

    private fun createInterpreter(context: Context): Interpreter {
        val model = FileUtil.loadMappedFile(context, MODEL)
        try {
            val compat = CompatibilityList()
            if (compat.isDelegateSupportedOnThisDevice) {
                val options = Interpreter.Options().addDelegate(GpuDelegate(compat.bestOptionsForThisDevice))
                return Interpreter(model, options)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "GPU delegate failed -- using CPU", e)
        }
        return Interpreter(model, Interpreter.Options().setNumThreads(4))
    }

    /** Runs one dummy inference so shader compilation happens before the first real frame. */
    @Synchronized
    fun warmUp() {
        val start = SystemClock.uptimeMillis()
        inputBuffer.rewind()
        interpreter.run(inputBuffer, output)
        Log.d(TAG, "WARMUP done in ${SystemClock.uptimeMillis() - start}ms")
    }

    /**
     * nv21: tightly packed NV21 frame in sensor orientation.
     * rotation: same degrees the app passes to Bitmap.rotate (90/180/270).
     * Returns finger boxes in pixels of the UPRIGHT frame.
     */
    @Synchronized
    fun detect(nv21: ByteArray, width: Int, height: Int, rotation: Int): List<RectF> {
        val swapped = rotation == 90 || rotation == 270
        val uw = if (swapped) height else width
        val uh = if (swapped) width else height

        val t0 = SystemClock.uptimeMillis()

        for (i in 0 until INPUT) {
            xMap[i] = ((i + 0.5f) * uw / INPUT).toInt().coerceIn(0, uw - 1)
            yMap[i] = ((i + 0.5f) * uh / INPUT).toInt().coerceIn(0, uh - 1)
        }

        inputFloats.clear()
        val ySize = width * height
        for (j in 0 until INPUT) {
            val yu = yMap[j]
            for (i in 0 until INPUT) {
                val xu = xMap[i]
                val sx: Int
                val sy: Int
                when (rotation) {
                    90 -> { sx = yu; sy = height - 1 - xu }
                    180 -> { sx = width - 1 - xu; sy = height - 1 - yu }
                    270 -> { sx = width - 1 - yu; sy = xu }
                    else -> { sx = xu; sy = yu }
                }
                val y = nv21[sy * width + sx].toInt() and 0xFF
                val uvIndex = ySize + (sy shr 1) * width + (sx and 1.inv())
                val v = (nv21[uvIndex].toInt() and 0xFF) - 128
                val u = (nv21[uvIndex + 1].toInt() and 0xFF) - 128
                inputFloats.put(clamp(y + 1.402f * v) / 255f)
                inputFloats.put(clamp(y - 0.344136f * u - 0.714136f * v) / 255f)
                inputFloats.put(clamp(y + 1.772f * u) / 255f)
            }
        }
        inputBuffer.rewind()
        val t1 = SystemClock.uptimeMillis()

        interpreter.run(inputBuffer, output)
        val t2 = SystemClock.uptimeMillis()

        val o = output[0]
        val candidates = ArrayList<Candidate>()
        for (i in 0 until NUM_BOXES) {
            val score = maxOf(o[6][i], o[7][i])
            if (score < CONF) continue
            val cx = o[0][i]
            val cy = o[1][i]
            val w = o[2][i]
            val h = o[3][i]
            candidates.add(Candidate(score, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
        }
        candidates.sortByDescending { it.score }
        val kept = ArrayList<Candidate>()
        for (c in candidates) {
            if (kept.none { iou(it, c) > NMS_IOU }) kept.add(c)
        }

        Log.d(TAG, "PREP=${t1 - t0}ms INFER=${t2 - t1}ms boxes=${kept.size}")

        return kept.map {
            RectF(
                (it.l * uw).coerceIn(0f, uw.toFloat()),
                (it.t * uh).coerceIn(0f, uh.toFloat()),
                (it.r * uw).coerceIn(0f, uw.toFloat()),
                (it.b * uh).coerceIn(0f, uh.toFloat())
            )
        }
    }

    private fun clamp(v: Float) = if (v < 0f) 0f else if (v > 255f) 255f else v

    private fun iou(a: Candidate, b: Candidate): Float {
        val iw = minOf(a.r, b.r) - maxOf(a.l, b.l)
        val ih = minOf(a.b, b.b) - maxOf(a.t, b.t)
        if (iw <= 0f || ih <= 0f) return 0f
        val inter = iw * ih
        val union = (a.r - a.l) * (a.b - a.t) + (b.r - b.l) * (b.b - b.t) - inter
        return if (union <= 0f) 0f else inter / union
    }
}