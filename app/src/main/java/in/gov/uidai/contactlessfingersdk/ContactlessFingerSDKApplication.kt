package `in`.gov.uidai.contactlessfingersdk

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject

@HiltAndroidApp
class ContactlessFingerSDKApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Explicit init, not relying on auto-init timing -- Hilt's
        // constructor injection for RegistrationActivity (which needs
        // FirebaseRemoteConfig) can run before Firebase's own
        // ContentProvider-based auto-init completes, per the crash trace.
        FirebaseApp.initializeApp(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}