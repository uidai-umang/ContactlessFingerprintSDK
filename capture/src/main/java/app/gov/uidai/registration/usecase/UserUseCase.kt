package app.gov.uidai.registration.usecase

import app.gov.uidai.registration.model.Fingerprint
import app.gov.uidai.registration.model.User

interface UserUseCase {
    suspend fun register(uidHash: String, user: User, fingerprints: List<Fingerprint>)

    suspend fun isUserRegistered(uidHash: String): Boolean

    suspend fun getUser(uidHash: String): User?

    suspend fun getUserAndFingerprint(uidHash: String): Pair<User?, List<Fingerprint>>

    suspend fun getStoredEmbeddings(uidHash: String): List<ByteArray>
}