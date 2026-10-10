package app.gov.uidai.registration.model.auth

import com.google.gson.annotations.SerializedName

data class AuthTokenRequest(
    @SerializedName("operator_ref_id")
    val operatorRefId: String
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class LogoutRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class AuthTokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)

data class LogoutResponse(
    @SerializedName("message")
    val message: String
)
