package app.gov.uidai.registration.usecase

import android.content.Context
import android.content.Intent
import app.gov.uidai.registration.model.SlapSubOption
import `in`.gov.uidai.core.CaptureSDK
import java.util.UUID

/**
 * Minimal, additive bridge for launching the capture module's slap capture
 * flow (SlapCaptureRoute) -- mirrors FingerSDKManagerImpl's PidOptions XML
 * request shape exactly (same in-process CaptureSDK.createIntent entry
 * point CaptureActivity already reads fingerType/txnId back out of), but
 * deliberately does NOT route through FingerSDKManager's
 * parseResponse -> FingerEmbedder.embed() pipeline: that pipeline assumes a
 * single fingerprint crop, and a whole-hand slap image isn't one.
 *
 * Result handling (marking a SlapSubOption complete, uploading the
 * whole-hand image) is intentionally out of scope for now -- see
 * CaptureMethodViewModel's existing TODOs for the same "future
 * backend/session work" boundary this stops at.
 */
object SlapCaptureLauncher {
    private const val WADH_KEY = "sgydIC09zzy6f8Lb3xaAqzKquKe9lFcNR9uTvYxFp+A="
    private const val LANGUAGE = "en"

    /** SlapSubOption -> the 'Left'/'Right' hand-type string CaptureActivity/SlapCaptureRoute expect. */
    private fun SlapSubOption.toExpectedHandType(): String = when (this) {
        SlapSubOption.LEFT_SLAP -> "Left"
        SlapSubOption.RIGHT_SLAP -> "Right"
        SlapSubOption.THUMBS -> throw IllegalArgumentException("Thumbs capture is not built -- Continue is disabled for it in CaptureMethodScreen")
    }

    fun createIntent(context: Context, purpose: String, slapSubOption: SlapSubOption): Intent {
        val txnId = UUID.randomUUID().toString()
        val handType = slapSubOption.toExpectedHandType()
        val requestXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<PidOptions ver=\"1.0\" env=\"S\">\n" +
                "   <Opts environment=\"staging\" fCount=\"\" fType=\"\" iCount=\"\" iType=\"\" pCount=\"\" pType=\"\" format=\"\" pidVer=\"2.0\" timeout=\"\" otp=\"\" wadh=\"${WADH_KEY}\" posh=\"\" />\n" +
                "   <CustOpts>\n" +
                "      <Param name=\"txnId\" value=\"${txnId}\"/>\n" +
                "      <Param name=\"purpose\" value=\"${purpose}\"/>\n" +
                "      <Param name=\"language\" value=\"${LANGUAGE}\"/>\n" +
                "      <Param name=\"fullImage\" value=\"true\"/>\n" +
                "      <Param name=\"croppedImage\" value=\"true\"/>\n" +
                "      <Param name=\"fingerType\" value=\"${handType}\"/>\n" +
                "   </CustOpts>\n" +
                "</PidOptions>".trimIndent()

        return CaptureSDK.createIntent(
            context = context,
            requestXml = requestXml,
            callingPackage = context.packageName
        )
    }
}
