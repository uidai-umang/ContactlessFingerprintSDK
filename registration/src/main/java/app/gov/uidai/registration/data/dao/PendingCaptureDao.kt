package app.gov.uidai.registration.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gov.uidai.registration.data.entity.PendingCaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingCaptureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: PendingCaptureEntity)

    // Returns all pending captures ordered by insertion — preserves capture sequence
    @Query("SELECT * FROM pending_captures ORDER BY id ASC")
    suspend fun getAll(): List<PendingCaptureEntity>

    // Returns pending captures for a specific resident — used on every click
    @Query("SELECT * FROM pending_captures WHERE residentPseudonymId = :residentId ORDER BY id ASC")
    suspend fun getByResidentId(residentId: String): List<PendingCaptureEntity>

    // Reactive stream of pending captures per resident —
    // UI observes this to update finger status badges in real time
    @Query("SELECT * FROM pending_captures WHERE residentPseudonymId = :residentId ORDER BY id ASC")
    fun observeByResidentId(residentId: String): Flow<List<PendingCaptureEntity>>

    // Reactive count of all pending captures —
    // WorkManager and sync indicator observe this
    @Query("SELECT COUNT(*) FROM pending_captures")
    fun observePendingCount(): Flow<Int>

    // Deletes all captures for a session after successful batch upload
    @Query("DELETE FROM pending_captures WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    // Deletes a single capture after successful single upload
    @Query("DELETE FROM pending_captures WHERE id = :id")
    suspend fun deleteById(id: Int)

    // Increments retry count per session after a failed upload attempt
    @Query("UPDATE pending_captures SET retryCount = retryCount + 1 WHERE sessionId = :sessionId")
    suspend fun incrementRetryCount(sessionId: String)

    // Deletes a single capture by resident + finger — used after successful upload when sessionId may be stale
    @Query("DELETE FROM pending_captures WHERE residentPseudonymId = :residentId AND fingerType = :fingerType")
    suspend fun deleteByResidentAndFingerType(residentId: String, fingerType: String)

    // Returns count of pending captures — used by WorkManager to decide if work is needed
    @Query("SELECT COUNT(*) FROM pending_captures")
    suspend fun getPendingCount(): Int
}