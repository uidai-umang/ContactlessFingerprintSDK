package app.gov.uidai.registration.usecase.impl

import app.gov.uidai.registration.usecase.UIDManager
import org.apache.commons.codec.digest.DigestUtils
import javax.inject.Inject
import javax.inject.Singleton


class UIDManagerImpl : UIDManager {

    override fun validateUID(uid: String): Boolean {
        return uid.length == 12 && uid.all { it.isDigit() }
    }

    override fun hashUID(uid: String): String {
        // TODO: Implement proper UID hashing algorithm as per requirements
        // Using SHA-256 for now as placeholder
        return DigestUtils.sha256Hex(uid)
    }
}