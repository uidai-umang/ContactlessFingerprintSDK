package app.gov.uidai.capture.ui.camera.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import app.gov.uidai.capture.R
import java.util.EnumMap
import java.util.Locale

class TimelineView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(ctx, attrs, defStyle) {

    enum class SegmentType { SUCCESS, FAILURE }

    data class Segment(val range: ClosedFloatingPointRange<Float>, val type: SegmentType)

    data class State(
        val segments: List<Segment> = emptyList(),
        val thresholds: List<Float> = emptyList(), // 0f..1f
        val current: Float = 0f                    // 0f..1f
    )

    // Config (dp → px once)
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // Visual sizes (tunable)
    private var lineStroke = dp(6f)
    private var tickHeight = dp(14f)
    private var labelTextSize = dp(12f)
    private var labelSpacing = dp(6f)
    private val currentMarkerWidth = dp(2f)
    private var currentMarkerRadius = dp(6f)
    private var titleTextSize = dp(16f)
    private var titleSpacing = dp(8f)

    // Paints
    private val paints = EnumMap<SegmentType, Paint>(SegmentType::class.java).apply {
        put(SegmentType.SUCCESS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.colorSuccess); strokeWidth = lineStroke
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        })
        put(SegmentType.FAILURE, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.md_theme_error); strokeWidth =
            lineStroke
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        })
    }

    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_inverseOnSurface)
        strokeWidth = dp(2f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_inverseOnSurface)
        textSize = labelTextSize
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_inverseOnSurface)
        textSize = titleTextSize; textAlign = Paint.Align.LEFT
    }

    // Cached metrics
    private val fm = Paint.FontMetrics()
    private var labelTextHeight = 0f
    private val titleFm = Paint.FontMetrics()
    private var titleTextHeight = 0f

    private var state = State()
    private var title: String = ""

    fun setState(newState: State) {
        state = newState
        invalidate()
    }

    fun setLabelFormatter(formatter: (Float) -> String) {
        labelFormatter = formatter
        requestLayout()
        invalidate()
    }

    fun setTitle(text: String) {
        title = text
        requestLayout()
        invalidate()
    }

    private var labelFormatter: (Float) -> String = { v ->
        String.format(Locale.US, "%.2f", v)
    }

    init {
        recomputeTextMetrics()
        recomputeTitleMetrics()
    }

    private fun recomputeTextMetrics() {
        textPaint.getFontMetrics(fm)
        labelTextHeight = fm.descent - fm.ascent
    }

    private fun recomputeTitleMetrics() {
        titlePaint.getFontMetrics(titleFm)
        titleTextHeight = titleFm.descent - titleFm.ascent
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        recomputeTextMetrics()
        recomputeTitleMetrics()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(c: Canvas) {
        val startX = paddingLeft.toFloat()
        val endX = width - paddingRight.toFloat()
        val contentW = (endX - startX).coerceAtLeast(1f)
        val lineY = height / 2f

        // Draw title if present
        if (title.isNotEmpty()) {
            val titleBaseline = lineY - tickHeight - labelSpacing - labelTextHeight -
                    titleSpacing - titleFm.ascent
            c.drawText(title, paddingLeft.toFloat(), titleBaseline, titlePaint)
        }

        // Segments
        for (seg in state.segments) {
            val x0 = startX + contentW * seg.range.start.coerceIn(0f, 1f)
            val x1 = startX + contentW * seg.range.endInclusive.coerceIn(0f, 1f)
            val paint = paints[seg.type] ?: continue
            c.drawLine(x0, lineY, x1, lineY, paint)
        }

        // Threshold labels ABOVE the line
        val thresholdLabelBaseline = lineY - tickHeight - labelSpacing - fm.ascent
        for (t in state.thresholds) {
            val x = (startX + contentW * t).coerceIn(startX, endX)
            val label = labelFormatter(t)
            val textW = textPaint.measureText(label)
            c.drawText(
                label,
                clampLabelX(x, textW, startX, endX),
                thresholdLabelBaseline,
                textPaint
            )
        }

        // Current marker BELOW the line
        val cx = (startX + contentW * state.current).coerceIn(startX, endX)
        RectF(
            cx - currentMarkerWidth,
            lineY - currentMarkerRadius,
            cx + currentMarkerWidth,
            lineY + currentMarkerRadius
        ).let {
            c.drawRect(it, currentPaint)
        }
        val currentLabelBaseline = lineY + labelSpacing - fm.ascent
        val curLabel = labelFormatter(state.current)
        val curTextW = textPaint.measureText(curLabel)
        c.drawText(
            curLabel,
            clampLabelX(cx, curTextW, startX, endX),
            currentLabelBaseline,
            textPaint
        )
    }

    private fun clampLabelX(anchorX: Float, textW: Float, minX: Float, maxX: Float): Float {
        val half = textW / 2f
        val left = (anchorX - half).coerceAtLeast(minX)
        val right = (anchorX + half).coerceAtMost(maxX)
        return if (right - left < textW) minX else left
    }
}
