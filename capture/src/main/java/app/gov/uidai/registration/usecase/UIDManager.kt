package app.gov.uidai.registration.usecase

interface UIDManager {
    fun sanitizeRefId(raw: String): String
    fun validateRefId(refId: String): Boolean
    fun hashUID(uid: String): String
}