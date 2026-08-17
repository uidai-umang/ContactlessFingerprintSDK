package app.gov.uidai.registration.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_captures")
data class PendingCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: String,
    val residentPseudonymId: String,
    val operatorId: String,
    val fingerType: String,
    val hand: String,
    val imageFilePath: String,
    val imageChecksum: String,
    val cameraModel: String,
    val cameraResolution: String,
    val deviceModel: String,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val encryptedSessionKey: String,
    val iv: String,
    val hmac: String,
    val thumbprint: String
)