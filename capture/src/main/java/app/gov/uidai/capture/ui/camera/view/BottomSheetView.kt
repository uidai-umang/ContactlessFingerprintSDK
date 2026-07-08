package app.gov.uidai.capture.ui.camera.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.ui.camera.model.Info
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.ui.camera.model.UIMessage
import com.google.android.material.progressindicator.LinearProgressIndicator
import app.gov.uidai.capture.R

class BottomSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val sheetTitle: TextView by lazy {
        findViewById(R.id.title_bottom_sheet)
    }
    private val sheetDescription: TextView by lazy {
        findViewById(R.id.desc_bottom_sheet)
    }
    private val errorButtonContainer: LinearLayout by lazy {
        findViewById(R.id.error_btn_container)
    }
    private val retakeBtn: Button by lazy {
        findViewById(R.id.btn_retake)
    }
    private val goBackBtn: Button by lazy {
        findViewById(R.id.btn_go_back)
    }
    private val successProgressContainer: LinearLayout by lazy {
        findViewById(R.id.success_progress_container)
    }
    private val progressBar: LinearProgressIndicator by lazy {
        findViewById(R.id.progress_bar)
    }
    private val timeline1: TimelineView by lazy {
        findViewById(R.id.timeline1_bottom_sheet)
    }
    private val timeline2: TimelineView by lazy {
        findViewById(R.id.timeline2_bottom_sheet)
    }
    private val timeline3: TimelineView by lazy {
        findViewById(R.id.timeline3_bottom_sheet)
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_bottom_sheet, this, true)
        visibility = GONE
    }

    fun updateContent(
        uiMessage: UIMessage,
        resultValue: Stage2ResultValue? = null
    ): BottomSheetView {
        sheetTitle.setText(uiMessage.titleRes)
        sheetDescription.setText(uiMessage.descriptionRes)
        sheetDescription.isVisible = true

        if (resultValue != null) {
            timeline1.setState(
                TimelineView.State(
                    segments = listOf(
                        TimelineView.Segment(0f..resultValue.blurThreshold, TimelineView.SegmentType.FAILURE),
                        TimelineView.Segment(resultValue.blurThreshold..1f, TimelineView.SegmentType.SUCCESS)
                    ),
                    thresholds = listOf(resultValue.blurThreshold),
                    current = resultValue.blurResult.confidence
                )
            )
            timeline1.setTitle("Blur Result:")
            timeline1.isVisible = true
            timeline2.setState(
                TimelineView.State(
                    segments = listOf(
                        TimelineView.Segment(0f..resultValue.glareThresholdMin, TimelineView.SegmentType.SUCCESS),
                        TimelineView.Segment(resultValue.glareThresholdMin..resultValue.glareThresholdMax, TimelineView.SegmentType.FAILURE),
                        TimelineView.Segment(resultValue.glareThresholdMax..1f, TimelineView.SegmentType.SUCCESS)
                    ),
                    thresholds = listOf(resultValue.glareThresholdMin, resultValue.glareThresholdMax),
                    current = resultValue.glareResult.confidence
                )
            )
            timeline2.setTitle("Glare Result:")
            timeline2.isVisible = true
            timeline3.setState(
                TimelineView.State(
                    segments = listOf(
                        TimelineView.Segment(0f..resultValue.brightnessThresholdMin, TimelineView.SegmentType.FAILURE),
                        TimelineView.Segment(resultValue.brightnessThresholdMin..resultValue.brightnessThresholdMax, TimelineView.SegmentType.SUCCESS),
                        TimelineView.Segment(resultValue.brightnessThresholdMax..1f, TimelineView.SegmentType.FAILURE)
                    ),
                    thresholds = listOf(resultValue.brightnessThresholdMin, resultValue.brightnessThresholdMax),
                    current = resultValue.brightnessResult.confidence
                )
            )
            timeline3.setTitle("Brightness Result:")
            timeline3.isVisible = true
        } else {
            timeline1.isVisible = false
            timeline2.isVisible = false
            timeline3.isVisible = false
        }

        when(uiMessage){
            is Error -> {
                sheetDescription.isVisible = false
                errorButtonContainer.isVisible = true
                successProgressContainer.isVisible = false
            }

            is Info.Success -> {
                errorButtonContainer.isVisible = false
                successProgressContainer.isVisible = true

                ObjectAnimator.ofInt(progressBar, "progress", 0, 100).apply {
                    duration = 3000 // 3 seconds
                    interpolator = LinearInterpolator() // steady speed
                    start()
                }
            }

            else -> {

            }
        }

        return this
    }

    fun showIfNotVisible() {
        if (!isVisible) show()
    }

    fun show() {
        // Ensure we have the measured height before starting the animation

        animate()
            .translationY(0f)
            .setDuration(100)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    visibility = VISIBLE
                }
            })
            .start()
    }

    fun hide() {
        animate()
            .translationY(height.toFloat()) // Animate off-screen to the bottom
            .setDuration(100)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    visibility = GONE // Hide the view after animation ends
                }
            })
            .start()
    }

    fun getTitle(): String {
        return sheetTitle.text.toString()
    }

    fun setRetakeButtonClickListener(
        listener: OnClickListener
    ) {
        retakeBtn.setOnClickListener(listener)
    }

    fun setGoBackButtonClickListener(
        listener: OnClickListener
    ) {
        goBackBtn.setOnClickListener(listener)
    }
}