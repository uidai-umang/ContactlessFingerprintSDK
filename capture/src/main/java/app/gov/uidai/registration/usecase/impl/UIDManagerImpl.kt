package app.gov.uidai.registration.usecase.impl

import app.gov.uidai.registration.usecase.UIDManager
import org.apache.commons.codec.digest.DigestUtils

class UIDManagerImpl : UIDManager {

    // Letters, digits, '-' and '_' only -- safe to use as a nav route segment.
    override fun sanitizeRefId(raw: String): String =
        raw.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
            .take(MAX_REF_ID_LENGTH)

    override fun validateRefId(refId: String): Boolean =
        refId.isNotEmpty() && sanitizeRefId(refId) == refId

    // Still used by SharedViewModel for demo-asset bootstrapping.
    override fun hashUID(uid: String): String = DigestUtils.sha256Hex(uid)

    private companion object {
        const val MAX_REF_ID_LENGTH = 64
    }
}