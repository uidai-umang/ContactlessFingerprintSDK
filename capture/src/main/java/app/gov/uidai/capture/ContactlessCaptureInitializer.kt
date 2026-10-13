
package app.gov.uidai.capture

import android.content.Context
import app.gov.uidai.capture.usecase.slap.SlapFrameAnalyzer
import app.gov.uidai.capture.utils.KotlinUtils
import com.google.firebase.FirebaseApp

/**
 * Initializes the library-level dependencies required by contactless capture.
 *
 * The host application owns its Application class and Hilt configuration.
 * This initializer must not create or depend on a separate SDK Application.
 */
object ContactlessCaptureInitializer {

    @Volatile
    private var contextInitialized = false

    /**
     * Initializes dependencies that require application context.
     *
     * Safe to call more than once.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (contextInitialized) return

        val applicationContext = context.applicationContext

        // Initialize Firebase when the host app supplies valid Firebase config.
        FirebaseApp.initializeApp(applicationContext)

        // Required by capture utilities that use a stored application context.
        KotlinUtils.init(applicationContext)

        contextInitialized = true
    }

    /**
     * Warms up the SLAP detector off the main thread.
     *
     * Call from a coroutine. SlapFrameAnalyzer manages its own
     * inference executor.
     */
    suspend fun warmUp(slapFrameAnalyzer: SlapFrameAnalyzer) {
        slapFrameAnalyzer.warmUp()
    }
}
