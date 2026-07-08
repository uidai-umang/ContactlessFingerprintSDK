package app.gov.uidai.capture.domain.model

enum class ProcessingStage {
    NA,
    BRIGHTNESS,
    LIVENESS,
    GLARE,
    FINGER_DETECTION,
    BLUR,
    SEGMENTATION
}