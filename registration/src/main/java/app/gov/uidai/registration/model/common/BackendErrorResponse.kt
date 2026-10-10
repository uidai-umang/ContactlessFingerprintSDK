package app.gov.uidai.registration.model.common

import com.google.gson.annotations.SerializedName

data class BackendErrorResponse(
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: Any? = null
)
