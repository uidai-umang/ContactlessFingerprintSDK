package app.gov.uidai.registration.data.remote.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val errorData: Any? = null
    ) : ApiResult<Nothing>()
}