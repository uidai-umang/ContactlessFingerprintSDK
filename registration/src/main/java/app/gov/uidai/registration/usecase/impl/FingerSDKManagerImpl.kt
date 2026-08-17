package app.gov.uidai.registration.usecase.impl

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import app.gov.uidai.registration.model.CLFingerprint
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.model.SDKResult
import app.gov.uidai.registration.usecase.FingerSDKManager
import app.gov.uidai.registration.utils.toBitmap
import com.gemalto.jp2.JP2Encoder
import `in`.gov.uidai.core.CaptureSDK
import `in`.gov.uidai.embedding.FingerEmbedder
import `in`.gov.uidai.utility.constants.ResultCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class FingerSDKManagerImpl(
    private val context: Context,
    private val fingerEmbedder: FingerEmbedder
) : FingerSDKManager {

    companion object {
        private val TAG = FingerSDKManagerImpl::class.simpleName
        private const val REQUEST_KEY = "request"
        private const val RESPONSE_KEY = "response"
        private const val WADH_KEY = "sgydIC09zzy6f8Lb3xaAqzKquKe9lFcNR9uTvYxFp+A="
        private const val LANGUAGE = "en"
    }

    private var listener: FingerSDKManager.ResultListener? = null

    override fun setResultListener(listener: FingerSDKManager.ResultListener?) {
        this.listener = listener
    }

    override fun captureFingerprint(
        activityResultLauncher: ActivityResultLauncher<Intent>,
        purpose: String,
        fingerPosition: FingerPosition
    ) {
        val txnId = UUID.randomUUID().toString()
        try {
            // REPLACED -- was Intent(SDK_ACTION) targeting a separate
            // installed app via implicit intent resolution. Now calls
            // CaptureSDK.createIntent directly -- the SDK's own sanctioned,
            // documented, in-process entry point (per CaptureSDK.kt's own
            // doc comment: "Consumers should never launch CoreActivity
            // directly"). No external app dependency, no risk of silently
            // failing to resolve if a standalone SDK APK isn't installed.
            val intent = CaptureSDK.createIntent(
                context = context,
                requestXml = buildSdkRequest(
                    txnId = txnId,
                    purpose = purpose,
                    wantFullImage = true,
                    wantCroppedImage = true,
                    fingerType = fingerPosition.name
                ),
                callingPackage = context.packageName
            )
            activityResultLauncher.launch(intent)
            Log.d(TAG, "SDK Launched (in-process, via CaptureSDK.createIntent)")
        } catch (e: Exception) {
            Log.e(TAG, "Error on SDK launch", e)
            listener?.onResult(SDKResult.Error("No SDK Found"))
        }
    }

    override suspend fun parseResponse(resultCode: Int, data: Intent?) {
        try {
            Log.d(TAG, "SDK Response - URI: ${data?.data}")
            when (resultCode) {
                ResultCode.SDK_SUCCESS -> {
                    val uri = data?.data
                    val base64String = uri?.let {
                        context.contentResolver.openInputStream(it)?.bufferedReader()
                            ?.use { reader -> reader.readText() }
                    }
                    val responseExtra = data?.getStringExtra(RESPONSE_KEY)
                    Log.d(TAG, "SDK Response - Extra: $responseExtra")
                    val (blurScore, brightnessScore, glareScore) = parseScoresFromResponseXml(responseExtra)
                    if (base64String != null) {
                        extractFingerprintFromResponse(
                            response = base64String,
                            blurScore = blurScore,
                            brightnessScore = brightnessScore,
                            glareScore = glareScore
                        )
                    } else {
                        listener?.onResult(SDKResult.Error("No response received from SDK"))
                    }
                }
                ResultCode.SDK_FAILED -> {
                    listener?.onResult(SDKResult.Error("Fingerprint Capture Failed"))
                }
                ResultCode.SDK_TIMEOUT -> {
                    listener?.onResult(SDKResult.Error("SDK Session Timed Out"))
                }
                ResultCode.SDK_USER_ABORT -> {
                    listener?.onResult(SDKResult.Error("SDK Session Aborted by User"))
                }
                else -> {
                    listener?.onResult(SDKResult.Error("Unexpected error occurred in SDK"))
                }
            }
        } catch (e: Exception) {
            listener?.onResult(SDKResult.Error("Unexpected error occurred while parsing the response"))
            Log.d(TAG, "Error parsing SDK response", e)
        }
    }

    private fun parseScoresFromResponseXml(xml: String?): Triple<Double, Double, Double> {
        if (xml == null) return Triple(0.0, 0.0, 0.0)
        fun extractAttr(name: String): Double =
            Regex("""$name="([\d.]+)"""").find(xml)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return Triple(
            extractAttr("blurScore"),
            extractAttr("brightnessScore"),
            extractAttr("glareScore")
        )
    }

    private fun buildSdkRequest(
        txnId: String,
        purpose: String,
        wantFullImage: Boolean,
        wantCroppedImage: Boolean,
        fingerType: String
    ): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<PidOptions ver=\"1.0\" env=\"S\">\n" +
                "   <Opts environment=\"staging\" fCount=\"\" fType=\"\" iCount=\"\" iType=\"\" pCount=\"\" pType=\"\" format=\"\" pidVer=\"2.0\" timeout=\"\" otp=\"\" wadh=\"${WADH_KEY}\" posh=\"\" />\n" +
                "   <CustOpts>\n" +
                "      <Param name=\"txnId\" value=\"${txnId}\"/>\n" +
                "      <Param name=\"purpose\" value=\"${purpose}\"/>\n" +
                "      <Param name=\"language\" value=\"${LANGUAGE}\"/>\n" +
                "      <Param name=\"fullImage\" value=\"${wantFullImage}\"/>\n" +
                "      <Param name=\"croppedImage\" value=\"${wantCroppedImage}\"/>\n" +
                "      <Param name=\"fingerType\" value=\"${fingerType}\"/>\n" +
                "   </CustOpts>\n" +
                "</PidOptions>".trimIndent()
    }

    private suspend fun extractFingerprintFromResponse(
        response: String,
        blurScore: Double,
        brightnessScore: Double,
        glareScore: Double
    ) {
        withContext(Dispatchers.Default) {
            val bitmap = response.toBitmap()
            val jp2ByteArray = bitmapToJp2(bitmap)
            try {
                val (embedding, fingerQuality) = fingerEmbedder.embed(jp2ByteArray)
                listener?.onResult(
                    SDKResult.Success(
                        CLFingerprint(
                            fingerPosition = FingerPosition.UNKNOWN,
                            bitmap = bitmap,
                            embedding = embedding,
                            fingerQuality = fingerQuality,
                            jp2ByteArray = jp2ByteArray,
                            blurScore = blurScore,
                            brightnessScore = brightnessScore,
                            glareScore = glareScore
                        )
                    )
                )
            } catch (e: Exception) {
                listener?.onResult(
                    SDKResult.Error(message = "Unable to generate embeddings of the captured image.")
                )
            }
        }
    }

    private fun bitmapToJp2(bitmap: Bitmap): ByteArray {
        return JP2Encoder(bitmap).encode()
    }
}