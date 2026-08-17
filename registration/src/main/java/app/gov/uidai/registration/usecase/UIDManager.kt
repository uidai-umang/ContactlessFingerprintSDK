package app.gov.uidai.registration.usecase

interface UIDManager {
    fun validateUID(uid: String): Boolean
    fun hashUID(uid: String): String
}


