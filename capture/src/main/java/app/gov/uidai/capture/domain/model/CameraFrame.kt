package app.gov.uidai.capture.domain.model

import android.graphics.RectF
import android.util.Size
import androidx.core.graphics.toRect
import app.gov.uidai.capture.utils.extension.cropNV21

/**
 * Raw camera frame data structure
 */
data class CameraFrame(
    val processingId: Long,
    private val byteArray: ByteArray,
    val width: Int,
    val height: Int,
    val yRowStride: Int,
    val timestamp: Long,
    val rotationDegrees: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CameraFrame
        if (!byteArray.contentEquals(other.byteArray)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (timestamp != other.timestamp) return false
        if (rotationDegrees != other.rotationDegrees) return false
        return true
    }

    override fun hashCode(): Int {
        var result = byteArray.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + rotationDegrees
        return result
    }

    fun getByteArray(
        requiresCropping: Boolean,
        cutoutRect: RectF
    ): Pair<ByteArray, Size> {
        if (requiresCropping) {
            return byteArray.cropNV21(width, height, cutoutRect.toRect())
        }
        return byteArray to Size(width, height)
    }
}