package app.gov.uidai.capture.domain.model

interface ImageProcessingMethod<T> {
    fun run(provider: ImageDataProvider): ProcessingResult<T>
}
