
package app.gov.uidai.registration

import android.content.Context
import android.content.Intent

/**
 * Public entry point for launching the contactless registration flow.
 *
 * The host application should use this API instead of launching
 * the SDK's standalone application through an implicit Intent.
 */
object ContactlessRegistrationSdk {

    /**
     * Creates an explicit Intent for the SDK registration activity.
     *
     * @param context Host application context or Activity context.
     * @param config Configuration required to start registration.
     */
    fun createIntent(context: Context): Intent {
        return Intent(context, RegistrationActivity::class.java)
    }
}

/**
 * Public configuration accepted by the SDK.
 */
data class RegistrationConfig(
    val operatorRefId: String
)
