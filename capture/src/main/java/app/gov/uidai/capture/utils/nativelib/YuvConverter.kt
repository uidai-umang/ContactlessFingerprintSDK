package app.gov.uidai.capture.utils.nativelib // <-- Change to your actual package name

import android.graphics.ImageFormat
import android.media.Image
import java.nio.ByteBuffer

object YuvConverter {
    init {
        System.loadLibrary("yuv_converter")
    }

    @Synchronized
    fun yuv420ToNv21(image: Image): ByteArray {
        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Image must be in YUV_420_888 format")
        }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer: ByteBuffer = yPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        val vBuffer: ByteBuffer = vPlane.buffer

        // Defensive validation -- native code assumes buffer.remaining() is
        // at least rowStride * height (Y) / rowStride * ((height+1)/2) (U,V).
        // On some devices (confirmed: Unisoc/SPRD chipsets) the delivered
        // buffer can be smaller than this, causing an out-of-bounds native
        // read/write -- a hard native crash that kills the whole process
        // and can't be caught with try/catch. Bail out to a safe Kotlin
        // fallback instead of risking the native call when this happens.
        val expectedYSize = yPlane.rowStride * image.height
        val expectedUvSize = uPlane.rowStride * ((image.height + 1) / 2)

        val buffersLookSafe =
            yBuffer.remaining() >= expectedYSize &&
                    uBuffer.remaining() >= expectedUvSize &&
                    vBuffer.remaining() >= expectedUvSize

        if (!buffersLookSafe) {
            android.util.Log.w(
                "YuvConverter",
                "Buffer size mismatch detected -- yRemaining=${yBuffer.remaining()} " +
                        "expectedY=$expectedYSize, uRemaining=${uBuffer.remaining()} " +
                        "expectedUV=$expectedUvSize -- falling back to safe Kotlin conversion"
            )
            return yuv420ToNv21Fallback(image)
        }

        return yuv420ToNv21Native(
            yBuffer, uBuffer, vBuffer,
            yPlane.rowStride, uPlane.rowStride, vPlane.rowStride,
            uPlane.pixelStride, vPlane.pixelStride,
            image.width, image.height
        )
    }

    /**
     * Pure-Kotlin fallback YUV420 -> NV21 conversion. Slower than the native
     * path, but bounds-safe -- reads only image.width/height worth of real
     * pixel data via the standard row/pixel stride indexing, never assumes
     * a buffer is larger than it actually reports.
     */
    private fun yuv420ToNv21Fallback(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)

        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        var pos = 0
        for (row in 0 until height) {
            val rowStart = row * yRowStride
            if (rowStart + width > yBuffer.remaining() + rowStart) break // safety
            for (col in 0 until width) {
                nv21[pos++] = yBuffer.get(rowStart + col)
            }
        }

        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        var uvPos = ySize
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val uIndex = row * uRowStride + col * uPixelStride
                val vIndex = row * uRowStride + col * uPixelStride
                if (uIndex < uBuffer.remaining() && vIndex < vBuffer.remaining()) {
                    nv21[uvPos++] = vBuffer.get(vIndex) // NV21: V then U
                    nv21[uvPos++] = uBuffer.get(uIndex)
                }
            }
        }

        return nv21
    }

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