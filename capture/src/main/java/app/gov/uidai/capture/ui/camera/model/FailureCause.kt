package app.gov.uidai.capture.ui.camera.model

interface FailureCause : UIMessage {
    fun toWarning(priority: Priority = Priority(100)): Warning {
        if (this is Error) {
            return Warning.New(
                titleRes = this.titleRes,
                descriptionRes = this.descriptionRes,
                imageRes = this.imageRes,
                priority = priority,
                processingStage = this.processingStage
            )
        }
        return this as Warning
    }

    fun toError(): Error {
        if (this is Warning) {
            return Error.New(
                titleRes = this.titleRes,
                descriptionRes = this.descriptionRes,
                imageRes = this.imageRes,
                processingStage = this.processingStage
            )
        }
        return this as Error
    }
}