package app.gov.uidai.capture.ui.camera.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import app.gov.uidai.capture.R
import app.gov.uidai.capture.domain.model.LiveCheckScore
import app.gov.uidai.capture.domain.model.LiveQualityScores

/**
 * Debug-only overlay showing the live Stage 1 quality scores (blur/brightness/glare/finger).
 * Purely a display of values already computed in [app.gov.uidai.capture.usecase.ImageProcessor];
 * does not affect capture pass/fail behavior.
 */
class LiveQualityScoresView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private class Row(root: View) {
        val value: TextView = root.findViewById(R.id.tv_value)
        val indicator: View = root.findViewById(R.id.view_indicator)
    }

    private val blurRow: Row
    private val brightnessRow: Row
    private val glareRow: Row
    private val fingerRow: Row

    init {
        LayoutInflater.from(context).inflate(R.layout.view_live_quality_scores, this, true)

        blurRow = bindStaticLabel(R.id.row_blur, "Blur")
        brightnessRow = bindStaticLabel(R.id.row_brightness, "Brightness")
        glareRow = bindStaticLabel(R.id.row_glare, "Glare")
        fingerRow = bindStaticLabel(R.id.row_finger, "Finger Detected")
    }

    private fun bindStaticLabel(rowId: Int, label: String): Row {
        val rowRoot = findViewById<View>(rowId)
        rowRoot.findViewById<TextView>(R.id.tv_label).text = label
        return Row(rowRoot)
    }

    fun bind(scores: LiveQualityScores) {
        bindRow(blurRow, scores.blur)
        bindRow(brightnessRow, scores.brightness)
        bindRow(glareRow, scores.glare)
        bindRow(fingerRow, scores.fingerDetected)
    }

    private fun bindRow(row: Row, score: LiveCheckScore) {
        row.value.text = if (score.acceptedMax == Float.MAX_VALUE) {
            "%.2f (min: %.2f)".format(score.currentValue, score.acceptedMin)
        } else {
            "%.2f (%.2f, %.2f)".format(score.currentValue, score.acceptedMin, score.acceptedMax)
        }
        row.indicator.isVisible = score.passed
    }
}
