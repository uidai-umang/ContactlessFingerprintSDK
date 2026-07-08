package app.gov.uidai.capture.domain.model

interface FailureCause

enum class Warning: FailureCause {
    Internal,
    NoFinger,
    FingerMisaligned,
    FingerTooFar,
    FingerTooClose,
    FingerTooMuchClose,
    LowLight,
    TooBright,
    Liveness,
    LivenessProcessing,
    Blur,
    Glare,
    FocusNotLocked,
}

enum class Error: FailureCause {
    Segmentation,
    Blur,
    SomethingWentWrong,
    ManualFailure
}
