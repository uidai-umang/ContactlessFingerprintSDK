package app.gov.uidai.registration.data.remote.network

enum class ErrorBehavior {
    // Show exact backend message; user likely submitted invalid data.
    SHOW_VALIDATION_MESSAGE,

    // Show message and do not retry; caller lacks permission.
    SHOW_FORBIDDEN,

    // Show message; local state referencing this resource should be cleared.
    SHOW_NOT_FOUND_RESET_STATE,

    // Treat as soft-success; the resource already exists from a prior attempt.
    TREAT_AS_DUPLICATE_SUCCESS,

    // Show message; semantic validation failed server-side.
    SHOW_SEMANTIC_ERROR,

    // Safe to retry via pending queue; genuine server fault.
    RETRY_VIA_QUEUE,

    // Unknown / not mapped; surface to user generically.
    UNKNOWN,
}

object ErrorCodeMapper {
    fun behaviorFor(code: Int?): ErrorBehavior = when (code) {
        400 -> ErrorBehavior.SHOW_VALIDATION_MESSAGE
        403 -> ErrorBehavior.SHOW_FORBIDDEN
        404 -> ErrorBehavior.SHOW_NOT_FOUND_RESET_STATE
        409 -> ErrorBehavior.TREAT_AS_DUPLICATE_SUCCESS
        422 -> ErrorBehavior.SHOW_SEMANTIC_ERROR
        500 -> ErrorBehavior.RETRY_VIA_QUEUE
        else -> ErrorBehavior.UNKNOWN
    }
}
