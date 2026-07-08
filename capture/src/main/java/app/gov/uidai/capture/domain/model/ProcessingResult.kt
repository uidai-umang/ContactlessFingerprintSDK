package app.gov.uidai.capture.domain.model


sealed class ProcessingResult<out T>(val passed: Boolean) {
    abstract val confidence: Float

    data class Passed<out T>(
        val data: T,
        override val confidence: Float
    ) : ProcessingResult<T>(true)

    data class Failed<out T>(
        val cause: FailureCause,
        val status: Int,
        override val confidence: Float,
        val data: T? = null,
        val exception: Exception? = null
    ) : ProcessingResult<T>(false)
}