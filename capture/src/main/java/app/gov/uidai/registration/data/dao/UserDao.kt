package app.gov.uidai.registration.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gov.uidai.registration.data.entity.UserEntity

@Dao
interface UserDao {
    
    @Query("SELECT * FROM users WHERE uidHash = :uidHash")
    suspend fun getUserByUidHash(uidHash: String): UserEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Query("DELETE FROM users WHERE uidHash = :uidHash")
    suspend fun deleteUser(uidHash: String)
    
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE uidHash = :uidHash)")
    suspend fun isUserRegistered(uidHash: String): Boolean
}
