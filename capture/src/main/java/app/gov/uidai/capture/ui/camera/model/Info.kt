package app.gov.uidai.capture.ui.camera.model

import app.gov.uidai.capture.R

sealed class Info(
    override val titleRes: Int,
    override val descriptionRes: Int,
    override val imageRes: Int
) : UIMessage {

    val id: String = this::class.simpleName!!

    data object CaptureNow : Info(
        titleRes = R.string.info_title_capture_now,
        descriptionRes = R.string.info_desc_capture_now,
        imageRes = R.drawable.ic_android_black_24dp
    )

    data object HoldStill : Info(
        titleRes = R.string.info_title_hold_still,
        descriptionRes = R.string.info_desc_hold_still,
        imageRes = R.drawable.ic_android_black_24dp
    )

    data object EvaluatingImageQuality : Info(
        titleRes = R.string.info_title_evaluating_image_quality,
        descriptionRes = R.string.info_desc_processing_image,
        imageRes = R.drawable.ic_android_black_24dp
    )

    data object CheckingFingerPresence : Info(
        titleRes = R.string.info_title_processing_segmentation,
        descriptionRes = R.string.info_desc_processing_image,
        imageRes = R.drawable.ic_android_black_24dp
    )

    data object Success : Info(
        titleRes = R.string.info_title_success,
        descriptionRes = R.string.info_desc_success,
        imageRes = R.drawable.ic_android_black_24dp
    )
}