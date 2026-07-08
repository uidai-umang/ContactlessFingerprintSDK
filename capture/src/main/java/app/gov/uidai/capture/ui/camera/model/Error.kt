package app.gov.uidai.capture.ui.camera.model

import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.R


sealed class Error(
    override val titleRes: Int,
    override val descriptionRes: Int,
    override val imageRes: Int,
    val processingStage: ProcessingStage,
) : UIMessage, FailureCause {

    val id: String = this::class.simpleName!!

    class New(
        titleRes: Int,
        descriptionRes: Int,
        imageRes: Int,
        processingStage: ProcessingStage,
    ) : Error(
        titleRes = titleRes,
        descriptionRes = descriptionRes,
        imageRes = imageRes,
        processingStage = processingStage
    )

    data object Segmentation : Error(
        titleRes = R.string.error_title_segmentation,
        descriptionRes = R.string.error_desc_segmentation,
        imageRes = R.drawable.ic_android_black_24dp,
        processingStage = ProcessingStage.SEGMENTATION
    )

    data object Blur : Error(
        titleRes = R.string.error_title_blur,
        descriptionRes = R.string.error_desc_blur,
        imageRes = R.drawable.ic_android_black_24dp,
        processingStage = ProcessingStage.BLUR
    )

    data object SomethingWentWrong : Error(
        titleRes = R.string.error_title_something_went_wrong,
        descriptionRes = R.string.error_desc_something_went_wrong,
        imageRes = R.drawable.ic_android_black_24dp,
        processingStage = ProcessingStage.NA
    )

    data object ManualFailure : Error(
        titleRes = R.string.error_title_manual,
        descriptionRes = R.string.error_desc_manual,
        imageRes = R.drawable.ic_android_black_24dp,
        processingStage = ProcessingStage.NA
    )
}