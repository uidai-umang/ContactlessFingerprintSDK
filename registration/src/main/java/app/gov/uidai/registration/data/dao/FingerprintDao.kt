package app.gov.uidai.registration.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gov.uidai.registration.data.entity.FingerprintEntity

@Dao
interface FingerprintDao {
    
    @Query("SELECT * FROM fingerprints WHERE uidHash = :uidHash")
    suspend fun getFingerprintsByUidHash(uidHash: String): List<FingerprintEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprint(fingerprint: FingerprintEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprints(fingerprints: List<FingerprintEntity>)
    
    @Query("DELETE FROM fingerprints WHERE uidHash = :uidHash")
    suspend fun deleteFingerprintsByUidHash(uidHash: String)
    
    @Query("SELECT COUNT(*) FROM fingerprints WHERE uidHash = :uidHash")
    suspend fun getFingerprintCount(uidHash: String): Int
}
