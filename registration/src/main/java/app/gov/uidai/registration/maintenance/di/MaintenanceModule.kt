
package app.gov.uidai.registration.maintenance.di

import android.content.Context
import app.gov.uidai.registration.R
import app.gov.uidai.registration.maintenance.FirebaseMaintenanceStatusProvider
import app.gov.uidai.registration.maintenance.MaintenanceStatusProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MaintenanceModule {

    private const val SDK_FIREBASE_APP_NAME =
        "contactless-registration-sdk"

    @Provides
    @Singleton
    fun provideSdkFirebaseApp(
        @ApplicationContext context: Context
    ): FirebaseApp {
        // Reuse the SDK-owned Firebase app if it has already been created.
        FirebaseApp.getApps(context)
            .firstOrNull { it.name == SDK_FIREBASE_APP_NAME }
            ?.let { return it }

        val options = FirebaseOptions.Builder()
            .setApplicationId(
                context.getString(R.string.sdk_firebase_app_id)
            )
            .setApiKey(
                context.getString(R.string.sdk_firebase_api_key)
            )
            .setProjectId(
                context.getString(R.string.sdk_firebase_project_id)
            )
            .build()

        return FirebaseApp.initializeApp(
            context,
            options,
            SDK_FIREBASE_APP_NAME
        ) ?: error("Unable to initialize SDK Firebase app")
    }

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(
        firebaseApp: FirebaseApp
    ): FirebaseRemoteConfig =
        FirebaseRemoteConfig.getInstance(firebaseApp)

    @Provides
    @Singleton
    fun provideMaintenanceStatusProvider(
        remoteConfig: FirebaseRemoteConfig
    ): MaintenanceStatusProvider =
        FirebaseMaintenanceStatusProvider(remoteConfig)
}
