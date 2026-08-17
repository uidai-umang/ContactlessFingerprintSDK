package app.gov.uidai.registration.data.remote.network

import app.gov.uidai.registration.model.capture.CaptureRequest
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object MultipartHelper {

    private val gson = Gson()

    // Converts a string value to a plain text RequestBody
    fun String.toRequestBody(): RequestBody =
        this.toRequestBody("text/plain".toMediaType())

    // Converts a numeric value to a plain text RequestBody
    fun Number.toRequestBody(): RequestBody =
        this.toString().toRequestBody("text/plain".toMediaType())

    // Converts a boolean value to a plain text RequestBody
    fun Boolean.toRequestBody(): RequestBody =
        this.toString().toRequestBody("text/plain".toMediaType())

    // Builds the image MultipartBody.Part from raw bytes
    // fileName includes fingerType so CEPH path is meaningful
    fun buildImagePart(imageBytes: ByteArray, fingerType: String): MultipartBody.Part {
        val imageBody = imageBytes.toRequestBody("image/jp2".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(
            name = "image",
            filename = "${fingerType}_${System.currentTimeMillis()}.jp2",
            body = imageBody
        )
    }

    // Builds all metadata RequestBody parts from a CaptureRequest
    fun buildMetadataParts(req: CaptureRequest): Map<String, RequestBody> {
        return mapOf(
            "session_id" to req.sessionId.toRequestBody(),
            "resident_pseudonym_id" to req.residentPseudonymId.toRequestBody(),
            "operator_id" to req.operatorId.toRequestBody(),
            "finger_type" to req.fingerType.toRequestBody(),
            "hand" to req.hand.toRequestBody(),
            "nfiq2_score" to req.nfiq2Score.toRequestBody(),
            "blur_score" to req.blurScore.toRequestBody(),
            "brightness_score" to req.brightnessScore.toRequestBody(),
            "glare_score" to req.glareScore.toRequestBody(),
            "attempt_count" to req.attemptCount.toRequestBody(),
            "degraded_flag" to req.degradedFlag.toRequestBody(),
            "image_checksum" to req.imageChecksum.toRequestBody(),
            "camera_model" to req.cameraModel.toRequestBody(),
            "camera_resolution" to req.cameraResolution.toRequestBody(),
            "device_model" to req.deviceModel.toRequestBody(),
            "encrypted_session_key" to req.encryptedSessionKey.toRequestBody("text/plain".toMediaTypeOrNull()),
            "iv" to req.iv.toRequestBody("text/plain".toMediaTypeOrNull()),
            "hmac" to req.hmac.toRequestBody("text/plain".toMediaTypeOrNull()),
            "thumbprint" to req.thumbprint.toRequestBody("text/plain".toMediaTypeOrNull())

        )
    }

    // Builds batch metadata as flat indexed PartMap
    // Keys follow pattern: session_id_0, finger_type_0, session_id_1 etc.
    // Backend reads form values by index to match each image to its metadata
    fun buildBatchMetadataParts(requests: List<CaptureRequest>): Map<String, RequestBody> {
        val parts = mutableMapOf<String, RequestBody>()
        requests.forEachIndexed { index, req ->
            parts["session_id_$index"] = req.sessionId.toRequestBody()
            parts["resident_pseudonym_id_$index"] = req.residentPseudonymId.toRequestBody()
            parts["operator_id_$index"] = req.operatorId.toRequestBody()
            parts["finger_type_$index"] = req.fingerType.toRequestBody()
            parts["hand_$index"] = req.hand.toRequestBody()
            parts["nfiq2_score_$index"] = req.nfiq2Score.toRequestBody()
            parts["blur_score_$index"] = req.blurScore.toRequestBody()
            parts["brightness_score_$index"] = req.brightnessScore.toRequestBody()
            parts["glare_score_$index"] = req.glareScore.toRequestBody()
            parts["attempt_count_$index"] = req.attemptCount.toRequestBody()
            parts["degraded_flag_$index"] = req.degradedFlag.toRequestBody()
            parts["image_checksum_$index"] = req.imageChecksum.toRequestBody()
            parts["camera_model_$index"] = req.cameraModel.toRequestBody()
            parts["camera_resolution_$index"] = req.cameraResolution.toRequestBody()
            parts["device_model_$index"] = req.deviceModel.toRequestBody()
            parts["encrypted_session_key_$index"] = req.encryptedSessionKey.toRequestBody("text/plain".toMediaTypeOrNull())
            parts["iv_$index"] = req.iv.toRequestBody("text/plain".toMediaTypeOrNull())
            parts["hmac_$index"] = req.hmac.toRequestBody("text/plain".toMediaTypeOrNull())
            parts["thumbprint_$index"] = req.thumbprint.toRequestBody("text/plain".toMediaTypeOrNull())
        }
        return parts
    }
}