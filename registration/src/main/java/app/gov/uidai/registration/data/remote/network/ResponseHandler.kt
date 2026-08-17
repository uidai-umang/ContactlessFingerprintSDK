package app.gov.uidai.registration.data.remote.network

import app.gov.uidai.registration.model.common.BackendErrorResponse
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ResponseHandler {

    private val gson = Gson()

    suspend fun <T> safeApiCall(
        call: suspend () -> Response<T>
    ): ApiResult<T> {
        return try {
            val response = call()
            handleResponse(response)
        } catch (e: Exception) {
            handleException(e)
        }
    }

    // Handles successful HTTP responses (2xx) — checks body is non-null
    private fun <T> handleResponse(response: Response<T>): ApiResult<T> {
        return if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Error("Empty response body", response.code())
            }
        } else {
            // Non-2xx response arrived — delegate to handleHttpError
            // for consistent status-code-based parsing
            handleHttpError(response.code(), response.errorBody()?.string())
        }
    }

    // Routes any thrown exception to the correct handler based on its type.
    // Does not assume — checks concrete exception types explicitly.
    private fun <T> handleException(e: Exception): ApiResult<T> {
        return when (e) {
            // HttpException is thrown by Retrofit when using suspend fun
            // that isn't wrapped in Response<T> and the server returns non-2xx.
            // Since our ClfApiService methods all return Response<T>, this path
            // is a safety net for any future non-Response suspend calls.
            is HttpException -> {
                val code = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                handleHttpError(code, errorBody)
            }

            is SocketTimeoutException ->
                ApiResult.Error("Request timed out. Please try again.", null, null)

            is UnknownHostException ->
                ApiResult.Error("Cannot reach server. Check your network connection.", null, null)

            is IOException ->
                ApiResult.Error("Network error. Please check your connection.", null, null)

            else ->
                ApiResult.Error(e.message ?: "Unexpected error occurred", null, null)
        }
    }

    // Single source of truth for handling any HTTP error status code.
    // Parses the error body and maps the code to the correct ApiResult.Error.
    private fun <T> handleHttpError(code: Int, errorBody: String?): ApiResult<T> {
        val (message, errorData) = parseErrorBody(code, errorBody)
        return ApiResult.Error(message, code, errorData)
    }

    // Parses backend error JSON. Tries BackendErrorResponse shape first
    // ({"message","data"}), falls back to default message for the code.
    private fun parseErrorBody(code: Int, errorBody: String?): Pair<String, Any?> {
        if (errorBody.isNullOrBlank()) return Pair(getDefaultMessage(code), null)

        return try {
            val parsed = gson.fromJson(errorBody, BackendErrorResponse::class.java)
            if (!parsed.message.isNullOrBlank()) {
                Pair(parsed.message, parsed.data)
            } else {
                Pair(getDefaultMessage(code), null)
            }
        } catch (_: JsonSyntaxException) {
            Pair(getDefaultMessage(code), null)
        }
    }

    private fun getDefaultMessage(code: Int): String = when (code) {
        400 -> "Bad request"
        401 -> "Unauthorised"
        403 -> "Access denied"
        404 -> "Not found"
        409 -> "Conflict — resource already exists"
        422 -> "Unprocessable entity"
        429 -> "Too many requests, try again later"
        500 -> "Internal server error"
        503 -> "Service unavailable"
        else -> "Something went wrong (code $code)"
    }
}