package app.gov.uidai.capture.ui.camera.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import app.gov.uidai.capture.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BottomCameraControllerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val manualCaptureContainer: LinearLayout by lazy {
        findViewById(R.id.manual_capture_button_container)
    }
    private val torchButton: ImageButton by lazy {
        findViewById(R.id.btn_torch)
    }
    private val captureButton: ImageButton by lazy {
        findViewById(R.id.btn_capture)
    }
    private val modeToggleGroup: ChipGroup by lazy {
        findViewById(R.id.mode_toggle_group)
    }
    private val autoChip: Chip by lazy {
        findViewById(R.id.chip_auto)
    }
    private val manualChip: Chip by lazy {
        findViewById(R.id.chip_manual)
    }
    private val transactionInfo: TextView by lazy {
        findViewById(R.id.tv_transaction_info)
    }
    private val manualFocusButton: ImageButton by lazy {
        findViewById(R.id.btn_manual_focus)
    }

    private var onCaptureModeChanged: ((Boolean) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(
            R.layout.view_camera_controller,
            this,
            true
        )
        // Set initial state without animation
        manualCaptureContainer.isVisible = modeToggleGroup.checkedChipId == R.id.chip_manual

        modeToggleGroup.setOnCheckedChangeListener { _, checkedId ->
            val isManual = checkedId == R.id.chip_manual
            if (isManual) {
                expand()
            } else {
                collapse()
            }
            onCaptureModeChanged?.invoke(isManual)
        }
    }

    private fun expand() {
        manualCaptureContainer.measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        val targetHeight = manualCaptureContainer.measuredHeight

        manualCaptureContainer.layoutParams.height = 0
        manualCaptureContainer.visibility = VISIBLE
        manualCaptureContainer.alpha = 0f

        val animator = ValueAnimator.ofInt(0, targetHeight).apply {
            duration = 300
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                manualCaptureContainer.layoutParams.height = value
                manualCaptureContainer.alpha = animation.animatedFraction
                manualCaptureContainer.requestLayout()
            }
        }
        animator.start()
    }

    private fun collapse() {
        val initialHeight = manualCaptureContainer.height
        val animator = ValueAnimator.ofInt(initialHeight, 0).apply {
            duration = 300
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                manualCaptureContainer.layoutParams.height = value
                manualCaptureContainer.alpha = 1f - animation.animatedFraction
                manualCaptureContainer.requestLayout()
                if (value == 0) {
                    manualCaptureContainer.visibility = GONE
                }
            }
        }
        animator.start()
    }

    fun setTransactionInfo(version: String?, txnId: String?, date: LocalDate = LocalDate.now()){
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val formatted = date.format(formatter)
        transactionInfo.text = context.getString(
            R.string.transaction_info,
            version, formatted, txnId
        )
    }

    fun setTorchState(isOn: Boolean) {
        torchButton.isSelected = isOn
    }

    fun setCaptureButtonEnabled(isEnabled: Boolean) {
        captureButton.isEnabled = isEnabled
    }

    fun setCaptureMode(isManual: Boolean) {
        val newChipId = if (isManual) R.id.chip_manual else R.id.chip_auto
        if (modeToggleGroup.checkedChipId != newChipId) {
            modeToggleGroup.check(newChipId)
        }
    }

    fun setOnCaptureModeChangeListener(listener: (Boolean) -> Unit) {
        onCaptureModeChanged = listener
    }

    fun disableCaptureModeToggling() {
        autoChip.isEnabled = false
        manualChip.isEnabled = false
    }

    fun enableCaptureModeToggling() {
        autoChip.isEnabled = true
        manualChip.isEnabled = true
    }

    fun setTorchButtonClickListener(l: OnClickListener) {
        torchButton.setOnClickListener(l)
    }

    fun setCaptureButtonClickListener(l: OnClickListener) {
        captureButton.setOnClickListener(l)
    }

    fun setManualFocusButtonClickListener(l: OnClickListener) {
        manualFocusButton.setOnClickListener(l)
    }
}