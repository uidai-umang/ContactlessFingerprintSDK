package `in`.gov.uidai.contactlessfingersdk

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.gov.uidai.capture.usecase.single.FingerYoloAnalyzer
import app.gov.uidai.capture.usecase.slap.SlapFrameAnalyzer
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject
import app.gov.uidai.capture.utils.KotlinUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class ContactlessFingerSDKApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var slapFrameAnalyzer: SlapFrameAnalyzer

    @Inject
    lateinit var fingerYoloAnalyzer: FingerYoloAnalyzer

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        KotlinUtils.init(this)
        CoroutineScope(Dispatchers.Default).launch {
            slapFrameAnalyzer.warmUp()
            fingerYoloAnalyzer.warmUp()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}