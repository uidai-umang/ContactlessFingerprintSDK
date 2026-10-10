package app.gov.uidai.capture.usecase.slap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** TEMP, test-only. Runs YOLO on a frame, logs row stats and saves annotated JPEGs. */
class SlapYoloProbe(private val context: Context) {

    companion object {
        private const val TAG = "SlapYoloProbe"
        private const val MODEL = "best_float32.tflite"
        private const val INPUT = 800
        private const val NUM_BOXES = 13125
        private const val CONF = 0.25f
        private const val NMS_IOU = 0.3f
        private const val MAX_SAVED = 60
        // rows 4,5,6,7
        private val ROW_COLORS = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
    }

    private val interpreter: Interpreter = run {
        val model = FileUtil.loadMappedFile(context, MODEL)
        val compat = CompatibilityList()
        Log.i(TAG, "GPU supported=${compat.isDelegateSupportedOnThisDevice}")
        if (compat.isDelegateSupportedOnThisDevice) {
            try {
                Interpreter(
                    model,
                    Interpreter.Options().addDelegate(GpuDelegate(compat.bestOptionsForThisDevice))
                ).also { Log.i(TAG, "Using GPU delegate") }
            } catch (e: Exception) {
                Log.e(TAG, "GPU delegate failed, falling back to CPU", e)
                Interpreter(model, Interpreter.Options().setNumThreads(4))
            }
        } else {
            Interpreter(model, Interpreter.Options().setNumThreads(4))
        }
    }

    private val input = ByteBuffer.allocateDirect(4 * INPUT * INPUT * 3).order(ByteOrder.nativeOrder())
    private val output = Array(1) { Array(8) { FloatArray(NUM_BOXES) } }
    private val pixels = IntArray(INPUT * INPUT)
    private val outDir = File(context.getExternalFilesDir(null), "yolo_probe").apply { mkdirs() }
    private var saved = 0
    private var frameNo = 0

    private data class Det(val box: RectF, val score: Float, val row: Int)

    @Synchronized
    fun probe(bitmap: Bitmap): List<RectF> {
        frameNo++
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT, INPUT, true)
        resized.getPixels(pixels, 0, INPUT, 0, 0, INPUT, INPUT)
        if (resized !== bitmap) resized.recycle()

        input.rewind()
        for (p in pixels) {
            input.putFloat((p shr 16 and 0xFF) / 255f)
            input.putFloat((p shr 8 and 0xFF) / 255f)
            input.putFloat((p and 0xFF) / 255f)
        }

        val t0 = SystemClock.uptimeMillis()
        interpreter.run(input, output)
        val ms = SystemClock.uptimeMillis() - t0

        val rows = output[0]

        // Per-row max and count above threshold, rows 4..7
        val stats = (4..7).joinToString(" ") { r ->
            "r$r(max=${"%.2f".format(rows[r].max())},n=${rows[r].count { it > CONF }})"
        }

        // Finger score = max(row 6, row 7). Rows 4 and 5 are logged but never drive boxes.
        val dets = ArrayList<Det>()
        for (i in 0 until NUM_BOXES) {
            val s6 = rows[6][i]
            val s7 = rows[7][i]
            val score = maxOf(s6, s7)
            if (score <= CONF) continue
            val cx = rows[0][i]; val cy = rows[1][i]; val w = rows[2][i]; val h = rows[3][i]
            dets.add(
                Det(
                    RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2),
                    score,
                    if (s6 >= s7) 6 else 7
                )
            )
        }
        val kept = nms(dets)

        Log.i(
            TAG,
            "frame=$frameNo inference=${ms}ms $stats boxes=${kept.size} " +
                    kept.joinToString { "[r${it.row} ${"%.2f".format(it.score)} w=${"%.2f".format(it.box.width())}]" }
        )

        if (saved < MAX_SAVED) save(bitmap, kept, ms)

        return kept.map {
            RectF(
                (it.box.left * bitmap.width).coerceIn(0f, bitmap.width.toFloat()),
                (it.box.top * bitmap.height).coerceIn(0f, bitmap.height.toFloat()),
                (it.box.right * bitmap.width).coerceIn(0f, bitmap.width.toFloat()),
                (it.box.bottom * bitmap.height).coerceIn(0f, bitmap.height.toFloat())
            )
        }

    }

    private fun nms(dets: List<Det>): List<Det> {
        val kept = ArrayList<Det>()
        for (d in dets.sortedByDescending { it.score }) {
            if (kept.none { iou(it.box, d.box) > NMS_IOU }) kept.add(d)
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val l = maxOf(a.left, b.left); val t = maxOf(a.top, b.top)
        val r = minOf(a.right, b.right); val btm = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, r - l) * maxOf(0f, btm - t)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun save(src: Bitmap, dets: List<Det>, ms: Long) {
        try {
            val copy = src.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(copy)
            val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = copy.width / 200f }
            val text = Paint().apply { textSize = copy.width / 30f; color = Color.WHITE; setShadowLayer(4f, 0f, 0f, Color.BLACK) }
            for (d in dets) {
                stroke.color = ROW_COLORS[d.row - 4]
                val r = RectF(d.box.left * copy.width, d.box.top * copy.height, d.box.right * copy.width, d.box.bottom * copy.height)
                canvas.drawRect(r, stroke)
                canvas.drawText("r${d.row} ${"%.2f".format(d.score)}", r.left, r.top - 4f, text)
            }
            canvas.drawText("${dets.size} boxes ${ms}ms", 10f, text.textSize + 10f, text)
            val file = File(outDir, "probe_%03d_n%d_%dms.jpg".format(saved, dets.size, ms))
            FileOutputStream(file).use { copy.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            copy.recycle()
            saved++
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
        }
    }

    fun close() = interpreter.close()
}