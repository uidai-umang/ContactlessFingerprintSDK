package app.gov.uidai.capture.ui.camera.mapper

typealias DomainWarning = app.gov.uidai.capture.domain.model.Warning
typealias DomainError = app.gov.uidai.capture.domain.model.Error
typealias DomainFailureCause = app.gov.uidai.capture.domain.model.FailureCause

fun DomainFailureCause.toUiFailureCause(): app.gov.uidai.capture.ui.camera.model.FailureCause = when (this) {
    is DomainWarning -> when (this) {
        DomainWarning.Internal -> app.gov.uidai.capture.ui.camera.model.Warning.Internal
        DomainWarning.NoFinger -> app.gov.uidai.capture.ui.camera.model.Warning.NoFinger
        DomainWarning.FingerMisaligned -> app.gov.uidai.capture.ui.camera.model.Warning.FingerMisaligned
        DomainWarning.FingerTooFar -> app.gov.uidai.capture.ui.camera.model.Warning.FingerTooFar
        DomainWarning.FingerTooClose -> app.gov.uidai.capture.ui.camera.model.Warning.FingerTooClose
        DomainWarning.FingerTooMuchClose -> app.gov.uidai.capture.ui.camera.model.Warning.FingerTooMuchClose
        DomainWarning.LowLight -> app.gov.uidai.capture.ui.camera.model.Warning.LowLight
        DomainWarning.TooBright -> app.gov.uidai.capture.ui.camera.model.Warning.TooBright
        DomainWarning.Liveness -> app.gov.uidai.capture.ui.camera.model.Warning.Liveness
        DomainWarning.LivenessProcessing -> app.gov.uidai.capture.ui.camera.model.Warning.LivenessProcessing
        DomainWarning.Blur -> app.gov.uidai.capture.ui.camera.model.Warning.Blur
        DomainWarning.Glare -> app.gov.uidai.capture.ui.camera.model.Warning.Glare
        DomainWarning.FocusNotLocked -> app.gov.uidai.capture.ui.camera.model.Warning.FocusNotLocked
    }

    is DomainError -> when (this) {
        DomainError.Segmentation -> app.gov.uidai.capture.ui.camera.model.Error.Segmentation
        DomainError.Blur -> app.gov.uidai.capture.ui.camera.model.Error.Blur
        DomainError.SomethingWentWrong -> app.gov.uidai.capture.ui.camera.model.Error.SomethingWentWrong
        DomainError.ManualFailure -> app.gov.uidai.capture.ui.camera.model.Error.ManualFailure
    }

    else -> {
        app.gov.uidai.capture.ui.camera.model.Error.SomethingWentWrong
    }
}
