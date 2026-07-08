package app.gov.uidai.capture.utils.nativelib

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap

/**
 * A high-performance bitmap rotator using native C++ code via the NDK.
 * Supports rotations of 90, 180, and 270 degrees.
 *
 * This version allocates a new output bitmap for each rotation call
 * to avoid cross-thread interference.
 */
object BitmapRotator {

    private const val TAG = "BitmapRotator"

    init {
        try {
            System.loadLibrary("bitmap_rotator")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library 'bitmap_rotator'", e)
        }
    }

    /**
     * Rotates a bitmap by a specified angle (90, 180, or 270 degrees).
     *
     * @param bitmap The source bitmap to rotate. Must be in ARGB_8888 format.
     * @param angle The angle of rotation. Must be 90, 180, or 270.
     * @return A new rotated bitmap instance.
     */
    fun rotate(bitmap: Bitmap, angle: Int): Bitmap {
        if (bitmap.config != Bitmap.Config.ARGB_8888) {
            Log.w(TAG, "Bitmap format is not ARGB_8888. Rotation may fail.")
        }

        val (targetWidth, targetHeight) = when (angle) {
            90, 270 -> bitmap.height to bitmap.width
            180 -> bitmap.width to bitmap.height
            else -> {
                Log.w(TAG, "Invalid angle: $angle. Returning original bitmap.")
                return bitmap
            }
        }

        // Allocate a fresh output bitmap for this rotation
        val outputBitmap = createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        // Call the native C++ function to perform the rotation
        rotateBitmap(bitmap, outputBitmap, angle)

        return outputBitmap
    }

    /**
     * Native method declaration.
     * The implementation is in the 'bitmap_rotator' C++ library.
     */
    private external fun rotateBitmap(bitmapIn: Bitmap, bitmapOut: Bitmap, angle: Int)
}
