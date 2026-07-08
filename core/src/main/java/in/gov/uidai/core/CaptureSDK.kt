package `in`.gov.uidai.core

import android.content.Context
import android.content.Intent
import `in`.gov.uidai.utility.constants.JourneyConstant


/**
 * Entry point for constructing Intents that launch the Capture SDK flow.
 *
 * This object provides a single utility method, [createIntent], which prepares
 * an [Intent] targeting [CoreActivity]. The intent carries the calling package
 * identifier and the request payload (in XML form) as extras, ensuring that
 * the Capture SDK can correctly identify the caller and process the request.
 *
 * ## Usage
 * ```
 * val intent = CaptureSDK.createIntent(
 *     context = this,
 *     callingPackage = packageName,
 *     requestXml = "<xml as string>"
 * )
 * startActivityForResult(intent, REQUEST_CODE_CAPTURE)
 * ```
 *
 * ## Extras Added
 * - [JourneyConstant.CALLING_PACKAGE] → The package name of the calling app.
 * - [JourneyConstant.REQUEST] → The request payload in XML format.
 *
 * ## Notes
 * - `callingPackage` may be `null` if the caller does not need to be identified.
 * - `requestXml` may be `null` if no request payload is required, but most SDK
 *   flows expect a valid XML string.
 * - The returned [Intent] is fully configured and ready to be passed to
 *   [Context.startActivity] or [ActivityResultLauncher].
 */
object CaptureSDK {
    fun createIntent(context: Context, requestXml: String?, callingPackage: String? = null): Intent {
        return Intent(context, CoreActivity::class.java).apply {
            putExtra(
                JourneyConstant.CALLING_PACKAGE,
                callingPackage
            )
            putExtra(
                JourneyConstant.REQUEST,
                requestXml
            )
        }
    }
}