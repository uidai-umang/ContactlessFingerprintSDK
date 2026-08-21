package app.gov.uidai.capture.usecase

import app.gov.uidai.capture.BuildConfig
import app.gov.uidai.capture.domain.model.CaptureStrategyType
import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

object ProcessingSettings : PreferenceGroup {
    override val title: String
        get() = "# Image Processor"

    val IMAGE_COUNT_FOR_STAGE2 = PreferenceParam(
        key = "global.image_count_for_stage_2",
        displayName = "Images Captured for Post Processing",
        type = PreferenceType.INT,
        defaultValue = 3
    )

    val SAVE_STAGE1_IMAGE = PreferenceParam(
        key = "global.save_stage1_image",
        displayName = "Save Stage1 Images",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val SAVE_STAGE1_UPRIGHT_IMAGE = PreferenceParam(
        key = "global.save_stage1_upright_image",
        displayName = "Save Stage1 Upright Images",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val SAVE_FINGER_CHECK_INPUT = PreferenceParam(
        key = "global.save_finger_check_input",
        displayName = "Save Finger Check Input",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val SAVE_MP_SEG_RESULT = PreferenceParam(
        key = "global.save_mp_seg_result",
        displayName = "Save Mediapipe Segmentation Result",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val SAVE_BLUR_INPUT = PreferenceParam(
        key = "global.save_blur_input",
        displayName = "Save Blur Input",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val SAVE_SHARP_IMAGES = PreferenceParam(
        key = "global.save_sharp_images",
        displayName = "Save Sharp Images",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val SAVE_SEGMENTATION_INPUT = PreferenceParam(
        key = "global.save_segmentation_input",
        displayName = "Save Segmentation Input",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val SAVE_FINAL_OUTPUT = PreferenceParam(
        key = "global.save_final_output",
        displayName = "Save Final Output",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val CROPPED_INPUT_TO_BLUR_MODEL = PreferenceParam(
        key = "global.cropped_input_to_blur_model",
        displayName = "Cropped Input To Blur Model",
        type = PreferenceType.BOOLEAN,
        defaultValue = true
    )

    val CROPPED_INPUT_TO_SEGMENTATION_MODEL = PreferenceParam(
        key = "global.cropped_input_to_segmentation_model",
        displayName = "Cropped Input To Segmentation Model",
        type = PreferenceType.BOOLEAN,
        defaultValue = false
    )

    val SHOW_LIVE_QUALITY_SCORES = PreferenceParam(
        key = "global.show_live_quality_scores",
        displayName = "Show Live Quality Scores",
        type = PreferenceType.BOOLEAN,
        defaultValue = BuildConfig.DEBUG
    )

    val CAPTURE_STRATEGY = PreferenceParam(
        key = "processing.capture_strategy",
        displayName = "Capture Strategy",
        type = PreferenceType.CHOICE(CaptureStrategyType.entries),
        defaultValue = CaptureStrategyType.FastStrategy
    )

    val ACCUMULATION_DELAY_OVERRIDE_MS = PreferenceParam(
        key = "processing.accumulation_delay_override_ms",
        displayName = "Accumulation Delay Override (ms, -1 = use strategy default)",
        type = PreferenceType.INT,
        defaultValue = -1
    )
}