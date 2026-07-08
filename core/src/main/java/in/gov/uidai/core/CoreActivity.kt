package `in`.gov.uidai.core

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.gov.uidai.capture.CaptureActivity
import `in`.gov.uidai.utility.constants.JourneyConstant
import `in`.gov.uidai.utility.constants.ResultCode

/**
 * Core entry point Activity for the Capture SDK.
 *
 * This activity acts as a thin wrapper around [CaptureActivity]. It is responsible for:
 * - Launching the capture module via [launchCaptureModule].
 * - Receiving the result from [CaptureActivity] through the [captureActivityLauncher].
 * - Mapping capture result codes ([ResultCode]) to corresponding SDK result codes.
 * - Returning the mapped result back to the original caller of the SDK.
 *
 * ## Lifecycle
 * - On creation ([onCreate]), the activity immediately launches [CaptureActivity].
 * - Once [CaptureActivity] finishes, the result is intercepted by [captureActivityLauncher].
 * - The result is translated into an SDK‑level result and passed back via [setResult].
 * - The activity then calls [finish] to terminate itself.
 *
 * ## Result Mapping
 * - [ResultCode.CAPTURE_SUCCESS] → [ResultCode.SDK_SUCCESS] (with data).
 * - [ResultCode.CAPTURE_FAILED] → [ResultCode.SDK_FAILED].
 * - [ResultCode.CAPTURE_TIMEOUT] → [ResultCode.SDK_TIMEOUT].
 * - [ResultCode.CAPTURE_USER_ABORT] → [ResultCode.SDK_USER_ABORT].
 * - Any other result → [Activity.RESULT_CANCELED].
 *
 * ## Usage
 * Consumers should never launch [CoreActivity] directly. Instead, use
 * [CaptureSDK.createIntent] to construct the proper [Intent] with required extras.
 *
 * Example:
 * ```
 * val intent = CaptureSDK.createIntent(context, packageName, "<xml as string>")
 * startActivityForResult(intent, REQUEST_CODE_CAPTURE)
 * ```
 *
 * ## Notes
 * - This activity is **transparent to the user**; it only orchestrates the capture flow.
 * - All logging is tagged with [TAG] for easy filtering in Logcat.
 * - Extras passed to [CoreActivity] (e.g., [JourneyConstant.CALLING_PACKAGE],
 *   [JourneyConstant.REQUEST]) are forwarded to [CaptureActivity].
 */
class CoreActivity: AppCompatActivity() {

    /**
     * Launcher for [CaptureActivity]. Handles result mapping and propagates
     * SDK‑level result codes back to the caller.
     */
    private val captureActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            ResultCode.CAPTURE_SUCCESS -> {
                Log.d(TAG, "ACTIVITY_RESULT -- OK")
                setResult(ResultCode.SDK_SUCCESS, result.data)
            }

            ResultCode.CAPTURE_FAILED -> {
                Log.d(TAG, "ACTIVITY_RESULT -- No encoded pid found")
                setResult(ResultCode.SDK_FAILED)
            }

            ResultCode.CAPTURE_TIMEOUT -> {
                Log.d(TAG, "ACTIVITY_RESULT -- Timed out")
                setResult(ResultCode.SDK_TIMEOUT)
            }

            ResultCode.CAPTURE_USER_ABORT -> {
                Log.d(TAG, "ACTIVITY_RESULT -- User Abort")
                setResult(ResultCode.SDK_USER_ABORT)
            }

            else -> {
                Log.d(TAG, "ACTIVITY_RESULT -- Exit")
                setResult(RESULT_CANCELED)
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch the CameraActivity to start the capture process
        launchCaptureModule()
    }

    /**
     * Prepares and launches [CaptureActivity] with the required extras.
     *
     * Extras forwarded:
     * - [JourneyConstant.CALLING_PACKAGE] → Caller package name.
     * - [JourneyConstant.INTENT_ACTION_FROM_AUA] → Original intent action.
     * - [JourneyConstant.REQUEST] → XML request payload.
     */
    private fun launchCaptureModule() {
        val intent = Intent(this, CaptureActivity::class.java).apply {
            putExtra(
                JourneyConstant.CALLING_PACKAGE,
                intent.getStringExtra(JourneyConstant.CALLING_PACKAGE) ?: callingPackage
            )
            putExtra(
                JourneyConstant.INTENT_ACTION_FROM_AUA,
                intent.action
            )
            putExtra(
                JourneyConstant.REQUEST,
                intent.getStringExtra(JourneyConstant.REQUEST)
            )
        }
        Log.d(TAG, "Capture Activity Launched")
        captureActivityLauncher.launch(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")
    }

    companion object {
        private val TAG = CoreActivity::class.simpleName
    }
}