package app.gov.uidai.registration.usecase

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import app.gov.uidai.registration.model.CLFingerprint
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.model.SDKResult

/**
 * Contract for managing fingerprint capture and parsing responses from the UIDAI
 * Contactless Fingerprint SDK.
 *
 * Implementations are responsible for:
 * - Launching the SDK capture flow via [captureFingerprint].
 * - Parsing the SDK response via [parseResponse].
 * - Delivering results to a [ResultListener].
 */
interface FingerSDKManager {
    /**
     * Registers a listener to receive fingerprint capture results.
     *
     * @param listener Callback invoked with [SDKResult] containing either a [CLFingerprint]
     *  on success or an error message on failure.
     */
    fun setResultListener(
        listener: ResultListener?
    )

    /**
     * Launches the fingerprint capture flow using the Fingerprint Capture SDK.
     *
     * @param activityResultLauncher The [ActivityResultLauncher] used to start the SDK activity.
     * @param purpose Business purpose for which the fingerprint is being captured.
     *
     */
    fun captureFingerprint(
        activityResultLauncher: ActivityResultLauncher<Intent>,
        purpose: String,
        fingerPosition: FingerPosition
    )

    /**
     * Parses the response returned by the SDK after capture.
     *
     * @param resultCode Result code returned by the SDK activity.
     * @param data Intent containing the SDK response payload.
     */
    suspend fun parseResponse(
        resultCode: Int,
        data: Intent?
    )

    /**
     * Listener for receiving fingerprint capture results.
     */
    fun interface ResultListener {
        /**
         * Called when the SDK returns a result.
         *
         * @param result [SDKResult.Success] containing a [CLFingerprint] if capture succeeded,
         *  or [SDKResult.Error] with a descriptive message if capture failed.
         */
        fun onResult(result: SDKResult<CLFingerprint>)
    }
}