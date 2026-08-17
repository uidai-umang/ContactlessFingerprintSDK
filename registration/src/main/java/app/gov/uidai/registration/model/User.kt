package app.gov.uidai.registration.model

data class User(
    val name: String,
    val phoneNumber: String,
    val fingerprintCount: Int = 0
)