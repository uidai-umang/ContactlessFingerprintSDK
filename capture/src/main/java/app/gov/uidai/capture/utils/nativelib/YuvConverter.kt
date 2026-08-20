package app.gov.uidai.capture.utils.nativelib // <-- Change to your actual package name

import android.graphics.ImageFormat
import android.media.Image
import java.nio.ByteBuffer

object YuvConverter {

    init {
        System.loadLibrary("yuv_converter")
    }

    /**
     * A high-performance helper function to convert a YUV_420_888 Image to an NV21 byte array
     * using a native C++ implementation.
     *
     * @param image The Image object from the Camera2 API.
     * @return The NV21 byte array.
     */
    @Synchronized
    fun yuv420ToNv21(image: Image): ByteArray {
        // Ensure the image is in the correct format
        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Image must be in YUV_420_888 format")
        }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer: ByteBuffer = yPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        val vBuffer: ByteBuffer = vPlane.buffer

        // Call the external C++ function
        return yuv420ToNv21Native(
            yBuffer,
            uBuffer,
            vBuffer,
            yPlane.rowStride,
            uPlane.rowStride,
            vPlane.rowStride,
            uPlane.pixelStride, // U and V planes have the same pixel stride
            vPlane.pixelStride,
            image.width,
            image.height
        )
    }

    // This declares the native function that is implemented in yuv_converter.jni
    private external fun yuv420ToNv21Native(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        yRowStride: Int,
        uRowStride: Int,
        vRowStride: Int,
        uPixelStride: Int,
        vPixelStride: Int,
        width: Int,
        height: Int
    ): ByteArray
}