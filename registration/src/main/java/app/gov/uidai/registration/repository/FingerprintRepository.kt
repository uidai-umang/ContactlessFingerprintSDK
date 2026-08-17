package app.gov.uidai.registration.repository

import app.gov.uidai.registration.data.entity.FingerprintEntity

interface FingerprintRepository {
    suspend fun getFingerprintsByUidHash(uidHash: String): List<FingerprintEntity>
    suspend fun insertFingerprint(fingerprint: FingerprintEntity)
    suspend fun insertFingerprints(fingerprints: List<FingerprintEntity>)
    suspend fun deleteFingerprintsByUidHash(uidHash: String)
    suspend fun getFingerprintCount(uidHash: String): Int
}
