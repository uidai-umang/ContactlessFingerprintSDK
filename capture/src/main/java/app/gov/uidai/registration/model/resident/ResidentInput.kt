package app.gov.uidai.registration.model.resident

const val MIN_RESIDENT_AGE_YEARS = 5

enum class Gender(val label: String) {
    MALE("Male"),
    FEMALE("Female")
}

// dob is yyyy-MM-dd, gender is MALE / FEMALE. Travels as nav route args.
data class ResidentInput(
    val refId: String,
    val dob: String,
    val gender: String
)