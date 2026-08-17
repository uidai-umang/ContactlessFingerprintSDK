package app.gov.uidai.registration.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.gov.uidai.registration.data.converter.FingerPositionConverter
import app.gov.uidai.registration.data.dao.FingerprintDao
import app.gov.uidai.registration.data.dao.PendingCaptureDao
import app.gov.uidai.registration.data.dao.UserDao
import app.gov.uidai.registration.data.entity.FingerprintEntity
import app.gov.uidai.registration.data.entity.PendingCaptureEntity
import app.gov.uidai.registration.data.entity.UserEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [UserEntity::class, FingerprintEntity::class, PendingCaptureEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(
    FingerPositionConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun fingerprintDao(): FingerprintDao

    abstract fun pendingCaptureDao(): PendingCaptureDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "contactless_registration_database"
        ).fallbackToDestructiveMigration(dropAllTables = false).build()
    }
    
    @Provides
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }
    
    @Provides
    fun provideFingerprintDao(database: AppDatabase): FingerprintDao {
        return database.fingerprintDao()
    }

    @Provides
    fun providePendingCaptureDao(database: AppDatabase): PendingCaptureDao {
        return database.pendingCaptureDao()
    }
}
