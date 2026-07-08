package app.gov.uidai.capture.ui.camera.model

import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.R

@JvmInline
value class Priority(private val level: Int) : Comparable<Priority> {
    override fun compareTo(other: Priority): Int = level.compareTo(other.level)
}

sealed class Warning(
    override val titleRes: Int,
    override val descriptionRes: Int,
    override val imageRes: Int,
    val priority: Priority,
    val processingStage: ProcessingStage,
) : UIMessage, FailureCause {

    val id: String = this::class.simpleName!!

    class New(
        titleRes: Int,
        descriptionRes: Int,
        imageRes: Int,
        priority: Priority,
        processingStage: ProcessingStage,
    ) : Warning(
        titleRes = titleRes,
        descriptionRes = descriptionRes,
        imageRes = imageRes,
        priority = priority,
        processingStage = processingStage
    )

    data object Internal : Warning(
        titleRes = R.string.warning_title_internal_error,
        descriptionRes = R.string.warning_desc_internal_error,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(0),
        processingStage = ProcessingStage.NA
    )

    data object NoFinger : Warning(
        titleRes = R.string.warning_title_no_finger,
        descriptionRes = R.string.warning_desc_no_finger,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(10),
        processingStage = ProcessingStage.FINGER_DETECTION
    )

    data object FingerMisaligned : Warning(
        titleRes = R.string.warning_title_finger_misaligned,
        descriptionRes = R.string.warning_desc_finger_misaligned,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(10),
        processingStage = ProcessingStage.FINGER_DETECTION
    )

    data object FingerTooFar : Warning(
        titleRes = R.string.warning_title_finger_too_far,
        descriptionRes = R.string.warning_desc_finger_too_far,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(10),
        processingStage = ProcessingStage.FINGER_DETECTION
    )

    data object FingerTooClose : Warning(
        titleRes = R.string.warning_title_finger_too_close,
        descriptionRes = R.string.warning_desc_finger_too_close,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(10),
        processingStage = ProcessingStage.FINGER_DETECTION
    )

    data object FingerTooMuchClose : Warning(
        titleRes = R.string.warning_title_finger_too_much_close,
        descriptionRes = R.string.warning_desc_finger_too_much_close,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(10),
        processingStage = ProcessingStage.FINGER_DETECTION
    )

    data object LowLight : Warning(
        titleRes = R.string.warning_title_low_light,
        descriptionRes = R.string.warning_desc_low_light,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(20),
        processingStage = ProcessingStage.BRIGHTNESS
    )

    data object TooBright : Warning(
        titleRes = R.string.warning_title_too_bright,
        descriptionRes = R.string.warning_desc_too_bright,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(20),
        processingStage = ProcessingStage.BRIGHTNESS
    )

    data object Liveness : Warning(
        titleRes = R.string.warning_title_liveness,
        descriptionRes = R.string.warning_desc_liveness,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(30),
        processingStage = ProcessingStage.LIVENESS
    )

    data object LivenessProcessing : Warning(
        titleRes = R.string.warning_title_liveness_waiting,
        descriptionRes = R.string.warning_desc_liveness_waiting,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(30),
        processingStage = ProcessingStage.LIVENESS
    )

    data object Blur : Warning(
        titleRes = R.string.warning_title_blur,
        descriptionRes = R.string.warning_desc_blur,
        imageRes = R.drawable.ic_android_black_24dp,
        processingStage = ProcessingStage.BLUR,
        priority = Priority(40)
    )

    data object Glare : Warning(
        titleRes = R.string.warning_title_glare,
        descriptionRes = R.string.warning_desc_glare,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(50),
        processingStage = ProcessingStage.GLARE
    )

    data object FocusNotLocked : Warning(
        titleRes = R.string.warning_title_focus_not_locked,
        descriptionRes = R.string.warning_desc_focus_not_locked,
        imageRes = R.drawable.ic_android_black_24dp,
        priority = Priority(60),
        processingStage = ProcessingStage.NA
    )
}

/**
 * Active warning with temporal information
 */
data class ActiveWarning(
    val warning: Warning,
    val activatedAt: Long,
    val lastSeenAt: Long,
    val consecutiveFailures: Int
)
