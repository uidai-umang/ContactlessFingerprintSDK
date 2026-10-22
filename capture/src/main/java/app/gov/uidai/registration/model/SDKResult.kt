package app.gov.uidai.registration.model

sealed class SDKResult<T> {
    data class Success<T>(val data: T) : SDKResult<T>()
    data class Error<T>(val message: String) : SDKResult<T>()
}