package app.gov.uidai.capture.usecase.slap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TEMP, test-only. Runs best_float32.tflite on live Slap frames and LOGS what
 * it sees; its output is never used for gating or capture. Delete this file
 * (and the 4 lines in SlapFrameAnalyzer) after the test.
 *
 * Logs, per probed frame (tag: SlapYoloProbe):
 *  - inference time
 *  - for EACH class row (rows 4..end of the output): max score, how many
 *    candidates > 0.25 and > 0.5, and the top NMS'd boxes
 * Also saves annotated frames (boxes drawn, colour per class row) to
 * <externalFiles>/yolo_probe/ so you can SEE whether boxes land on fingers.
 */
class SlapYoloProbe(
    @ApplicationContext private val context: Context,
    modelPath: String = "best_float32.tflite"
) {

    companion object {
        private const val TAG = "SlapYoloProbe"
        private const val MIN_SCORE = 0.25f
        private const val NMS_IOU = 0.3f
        private const val TOP_K = 6
        private const val MAX_SAVED = 60
        private const val SAVE_WIDTH = 800
        private val ROW_COLORS = intArrayOf(
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA
        )
    }

    private class Det(val box: RectF, val score: Float)

    private val interpreter: Interpreter = run {
        val model = FileUtil.loadMappedFile(context, modelPath)
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
    private val inH: Int
    private val inW: Int
    private val rows: Int   // 4 box values + class scores
    private val cands: Int  // candidate boxes
    private val ready: Boolean

    init {
        val inShape = interpreter.getInputTensor(0).shape()
        val outShape = interpreter.getOutputTensor(0).shape()
        Log.i(TAG, "model=$modelPath input=${inShape.contentToString()} output=${outShape.contentToString()}")

        ready = inShape.size == 4 && outShape.size == 3 && outShape[1] < outShape[2]
        inH = if (inShape.size == 4) inShape[1] else 0
        inW = if (inShape.size == 4) inShape[2] else 0
        rows = if (outShape.size == 3) outShape[1] else 0
        cands = if (outShape.size == 3) outShape[2] else 0

        if (!ready) Log.w(TAG, "Unexpected tensor layout (expected [1,H,W,3] -> [1,rows,cands]) -- probe disabled")
    }

    private val pixels = IntArray(inW * inH)
    private val inputBuf: ByteBuffer =
        ByteBuffer.allocateDirect(4 * inW * inH * 3).order(ByteOrder.nativeOrder())
    private val output = Array(1) { Array(rows) { FloatArray(cands) } }
    private var saved = 0

    fun probe(frame: Bitmap) {
        if (!ready) return

        val resized = Bitmap.createScaledBitmap(frame, inW, inH, true)
        resized.getPixels(pixels, 0, inW, 0, 0, inW, inH)
        if (resized !== frame) resized.recycle()

        inputBuf.rewind()
        for (p in pixels) {
            inputBuf.putFloat((p shr 16 and 0xFF) / 255f)
            inputBuf.putFloat((p shr 8 and 0xFF) / 255f)
            inputBuf.putFloat((p and 0xFF) / 255f)
        }

        val t0 = SystemClock.uptimeMillis()
        interpreter.run(inputBuf, output)
        val ms = SystemClock.uptimeMillis() - t0

        val out = output[0]
        val perRow = ArrayList<List<Det>>()
        Log.i(TAG, "---- inference ${ms}ms (frame ${frame.width}x${frame.height}) ----")

        for (r in 4 until rows) {
            var max = 0f
            var n25 = 0
            var n50 = 0
            val hits = ArrayList<Det>()
            for (i in 0 until cands) {
                val s = out[r][i]
                if (s > max) max = s
                if (s > 0.5f) n50++
                if (s > MIN_SCORE) {
                    n25++
                    hits.add(Det(toBox(out[0][i], out[1][i], out[2][i], out[3][i]), s))
                }
            }
            val kept = nms(hits).take(TOP_K)
            perRow.add(kept)
            Log.i(
                TAG,
                "row$r max=%.2f n>0.25=%d n>0.5=%d top=%s".format(
                    max, n25, n50,
                    kept.joinToString { d ->
                        "[cx=%.2f cy=%.2f w=%.2f h=%.2f @%.2f]".format(
                            d.box.centerX(), d.box.centerY(), d.box.width(), d.box.height(), d.score
                        )
                    }
                )
            )
        }

        if (perRow.any { it.isNotEmpty() } && saved < MAX_SAVED) {
            saveAnnotated(frame, perRow)
        }
    }

    // Output is normalised 0..1 (what YOLOAnalyser assumes); if the export
    // emits pixel units instead, scale down so logs/overlays stay comparable.
    private fun toBox(cx: Float, cy: Float, w: Float, h: Float): RectF {
        val px = maxOf(cx, cy, w, h) > 1.5f
        val x = if (px) cx / inW else cx
        val y = if (px) cy / inH else cy
        val bw = if (px) w / inW else w
        val bh = if (px) h / inH else h
        return RectF(x - bw / 2, y - bh / 2, x + bw / 2, y + bh / 2)
    }

    private fun nms(dets: List<Det>): List<Det> {
        val kept = ArrayList<Det>()
        for (d in dets.sortedByDescending { it.score }) {
            if (kept.none { iou(it.box, d.box) > NMS_IOU }) kept.add(d)
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val l = maxOf(a.left, b.left)
        val t = maxOf(a.top, b.top)
        val r = minOf(a.right, b.right)
        val bt = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, r - l) * maxOf(0f, bt - t)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun saveAnnotated(frame: Bitmap, perRow: List<List<Det>>) {
        try {
            val scale = SAVE_WIDTH.toFloat() / frame.width
            val w = SAVE_WIDTH
            val h = (frame.height * scale).toInt()
            val canvasBmp = Bitmap.createScaledBitmap(frame, w, h, true)
                .copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(canvasBmp)
            val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f }
            val label = Paint().apply { textSize = 28f; style = Paint.Style.FILL }

            perRow.forEachIndexed { idx, dets ->
                val color = ROW_COLORS[idx % ROW_COLORS.size]
                stroke.color = color
                label.color = color
                dets.forEach { d ->
                    val b = d.box
                    canvas.drawRect(b.left * w, b.top * h, b.right * w, b.bottom * h, stroke)
                    canvas.drawText("r${idx + 4} %.2f".format(d.score), b.left * w + 4, b.top * h + 28, label)
                }
            }

            val dir = File(context.getExternalFilesDir(null), "yolo_probe").apply { mkdirs() }
            val file = File(dir, "probe_%03d.jpg".format(saved))
            FileOutputStream(file).use { canvasBmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            saved++
            Log.i(TAG, "saved annotated frame -> ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "failed to save annotated frame", e)
        }
    }
}