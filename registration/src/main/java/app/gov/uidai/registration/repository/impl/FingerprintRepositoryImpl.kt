package app.gov.uidai.registration.repository.impl

import app.gov.uidai.registration.data.dao.FingerprintDao
import app.gov.uidai.registration.data.entity.FingerprintEntity
import app.gov.uidai.registration.repository.FingerprintRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FingerprintRepositoryImpl @Inject constructor(
    private val fingerprintDao: FingerprintDao
) : FingerprintRepository {

    override suspend fun getFingerprintsByUidHash(uidHash: String): List<FingerprintEntity> {
        return fingerprintDao.getFingerprintsByUidHash(uidHash)
    }

    override suspend fun insertFingerprint(fingerprint: FingerprintEntity) {
        fingerprintDao.insertFingerprint(fingerprint)
    }

    override suspend fun insertFingerprints(fingerprints: List<FingerprintEntity>) {
        fingerprintDao.insertFingerprints(fingerprints)
    }

    override suspend fun deleteFingerprintsByUidHash(uidHash: String) {
        fingerprintDao.deleteFingerprintsByUidHash(uidHash)
    }

    override suspend fun getFingerprintCount(uidHash: String): Int {
        return fingerprintDao.getFingerprintCount(uidHash)
    }
}