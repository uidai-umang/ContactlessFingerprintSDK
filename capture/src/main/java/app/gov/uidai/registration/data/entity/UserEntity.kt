package app.gov.uidai.registration.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uidHash: String,
    val name: String,
    val phoneNumber: String,
    val createdAt: Long = System.currentTimeMillis()
)
