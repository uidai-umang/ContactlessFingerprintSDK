package app.gov.uidai.capture.ui.camera.model

import app.gov.uidai.capture.domain.model.ProcessingResult

data class Stage2ResultValue(
    val blurResult: ProcessingResult<*>,
    val brightnessResult: ProcessingResult<*>,
    val glareResult: ProcessingResult<*>,
    val blurThreshold: Float,
    val glareThresholdMin: Float,
    val glareThresholdMax: Float,
    val brightnessThresholdMin: Float,
    val brightnessThresholdMax: Float
)