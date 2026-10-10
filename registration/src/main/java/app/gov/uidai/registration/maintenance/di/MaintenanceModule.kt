package app.gov.uidai.registration.maintenance.di

import app.gov.uidai.registration.maintenance.FirebaseMaintenanceStatusProvider
import app.gov.uidai.registration.maintenance.MaintenanceStatusProvider
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MaintenanceModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig = Firebase.remoteConfig

    @Provides
    @Singleton
    fun provideMaintenanceStatusProvider(
        remoteConfig: FirebaseRemoteConfig
    ): MaintenanceStatusProvider = FirebaseMaintenanceStatusProvider(remoteConfig)
}