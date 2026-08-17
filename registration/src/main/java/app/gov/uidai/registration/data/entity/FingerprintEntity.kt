package app.gov.uidai.registration.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.gov.uidai.registration.model.FingerPosition

@Entity(
    tableName = "fingerprints",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["uidHash"],
        childColumns = ["uidHash"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["uidHash"])]
)
data class FingerprintEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uidHash: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embeddingData: ByteArray,
    val fingerPosition: FingerPosition,
    val minutiaCount: Double,
    val contactArea: Double,
    val propreietaryQuality: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FingerprintEntity

        if (id != other.id) return false
        if (createdAt != other.createdAt) return false
        if (uidHash != other.uidHash) return false
        if (!embeddingData.contentEquals(other.embeddingData)) return false
        if (fingerPosition != other.fingerPosition) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + uidHash.hashCode()
        result = 31 * result + embeddingData.contentHashCode()
        result = 31 * result + fingerPosition.hashCode()
        return result
    }
}
