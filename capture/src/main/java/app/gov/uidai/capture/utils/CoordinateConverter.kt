package app.gov.uidai.capture.utils

import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.params.MeteringRectangle
import android.util.Log
import android.util.Size
import android.util.SizeF
import kotlin.math.roundToInt

private const val TAG = "CoordinateConverter"

fun convertCroppedRectToSensorMeteringRect(
    fingerRectInCroppedImage: RectF,
    cutoutRectInFullImage: RectF,
    fullImageSize: Size,
    sensorActiveArraySize: Rect
): MeteringRectangle? {
    // Step 1: Translate from Cropped Image to Full Image Coordinates
    // This gives us the finger's position relative to the entire, un-rotated camera frame.
    val fingerRectInFullImage = RectF(
        cutoutRectInFullImage.left + fingerRectInCroppedImage.left,
        cutoutRectInFullImage.top + fingerRectInCroppedImage.top,
        cutoutRectInFullImage.left + fingerRectInCroppedImage.right,
        cutoutRectInFullImage.top + fingerRectInCroppedImage.bottom
    )

    // Step 2: Scale from Full Image to Sensor Coordinates
    // The sensor's active array is often a different resolution than the image stream.
    // We need to scale our rectangle to match the sensor's coordinate system.
    val scaleX = sensorActiveArraySize.width().toFloat() / fullImageSize.width
    val scaleY = sensorActiveArraySize.height().toFloat() / fullImageSize.height

    val fingerRectInSensorCoords = Rect(
        (fingerRectInFullImage.left * scaleX).roundToInt(),
        (fingerRectInFullImage.top * scaleY).roundToInt(),
        (fingerRectInFullImage.right * scaleX).roundToInt(),
        (fingerRectInFullImage.bottom * scaleY).roundToInt()
    )

    // Step 3: Clamp the coordinates to be within the sensor's active area.
    // This is crucial to prevent crashes if the calculation is slightly out of bounds.
    val clampedRect = Rect(
        fingerRectInSensorCoords.left.coerceIn(sensorActiveArraySize.left, sensorActiveArraySize.right),
        fingerRectInSensorCoords.top.coerceIn(sensorActiveArraySize.top, sensorActiveArraySize.bottom),
        fingerRectInSensorCoords.right.coerceIn(sensorActiveArraySize.left, sensorActiveArraySize.right),
        fingerRectInSensorCoords.bottom.coerceIn(sensorActiveArraySize.top, sensorActiveArraySize.bottom)
    )

    // If the rectangle is invalid (e.g., width/height is zero or negative), return null.
    if (clampedRect.width() <= 0 || clampedRect.height() <= 0) {
        Log.w(TAG, "Invalid metering rectangle calculated, skipping focus update.")
        return null
    }

    // Step 4: Create the final MeteringRectangle with maximum weight.
    // This tells the camera that this area is of the highest priority.
    return MeteringRectangle(clampedRect, MeteringRectangle.METERING_WEIGHT_MAX)
}

fun getCutoutRectInImageCoordinates(
    imageSize: Size,
    totalRotation: Int,
    overlayViewCutoutRect: RectF,
    viewFinderSize: Size,
    overlayViewOrigin: Point
): RectF {
    // Since the preview and capture streams now have the same aspect ratio,
    // and the viewfinder is matched to the preview's aspect ratio,
    // the mapping is a direct proportional scaling.

    val viewFinderCutoutRect = RectF(
        overlayViewCutoutRect.left + overlayViewOrigin.x,
        overlayViewCutoutRect.top + overlayViewOrigin.y,
        overlayViewCutoutRect.right + overlayViewOrigin.x,
        overlayViewCutoutRect.bottom + overlayViewOrigin.y
    )

    // Account for rotation to use the correct dimensions for scaling
    val rotatedImageSize = when (totalRotation) {
        90, 270 -> Size(imageSize.height, imageSize.width)
        else -> imageSize
    }

    val scaleX = rotatedImageSize.width.toFloat() / viewFinderSize.width
    val scaleY = rotatedImageSize.height.toFloat() / viewFinderSize.height

    val finalRectInRotatedCaptureCoords = RectF(
        viewFinderCutoutRect.left * scaleX,
        viewFinderCutoutRect.top * scaleY,
        viewFinderCutoutRect.right * scaleX,
        viewFinderCutoutRect.bottom * scaleY
    )

    // Reverse the rotation to map the coordinates back to the sensor's native orientation.
    val finalRect = when (totalRotation) {
        90 -> RectF(
            finalRectInRotatedCaptureCoords.top,
            rotatedImageSize.width.toFloat() - finalRectInRotatedCaptureCoords.right,
            finalRectInRotatedCaptureCoords.bottom,
            rotatedImageSize.width.toFloat() - finalRectInRotatedCaptureCoords.left
        )
        180 -> RectF(
            rotatedImageSize.width.toFloat() - finalRectInRotatedCaptureCoords.right,
            rotatedImageSize.height.toFloat() - finalRectInRotatedCaptureCoords.bottom,
            rotatedImageSize.width.toFloat() - finalRectInRotatedCaptureCoords.left,
            rotatedImageSize.height.toFloat() - finalRectInRotatedCaptureCoords.top
        )
        270 -> RectF(
            rotatedImageSize.height.toFloat() - finalRectInRotatedCaptureCoords.bottom,
            finalRectInRotatedCaptureCoords.left,
            rotatedImageSize.height.toFloat() - finalRectInRotatedCaptureCoords.top,
            finalRectInRotatedCaptureCoords.right
        )
        else -> finalRectInRotatedCaptureCoords // 0 or default
    }
    return finalRect
}

fun calculateFingerDistance(
    fingerRect: RectF,
    imageSize: Size,
    sensorPhysicalSize: SizeF,
    focalLengthMM: Float,
    averageFingerWidthMM: Float
): Float {
    val perceivedWidthPixels = fingerRect.height()
    val imageWidthPixels = imageSize.width.toFloat()

    // Guard clause: Cannot calculate distance for an object with no width.
    if (perceivedWidthPixels <= 0) return 0f

    // Since the fingerRect is from the un-rotated (landscape) image, the relevant
    // physical sensor dimension that corresponds to the image's width is the sensor's physical width.
    val sensorWidthMM = sensorPhysicalSize.width

    // The core similar triangles formula:
    // Distance = (FocalLength * RealObjectWidth * ImageWidth) / (PerceivedObjectWidth * SensorWidth)
    // All units must be consistent (e.g., mm and pixels).
    val distanceM = 2 * (focalLengthMM * averageFingerWidthMM * imageWidthPixels) /
            (perceivedWidthPixels * sensorWidthMM) / 1000

    Log.d(TAG, "FINGER DISTANCE -- ${distanceM}m")

    return distanceM
}

/**
 * Inverse of calculateFingerDistance() above: given the closest distance
 * the lens can actually focus at (diopters -- either the real hardware
 * LENS_INFO_MINIMUM_FOCUS_DISTANCE, or a simulated cap), returns the
 * largest fraction of the frame WIDTH a finger can ever occupy while still
 * being in focus.
 *
 * If your cutout/goodArea geometry demands a bigger fraction than this
 * returns, the device physically cannot deliver a focused capture at that
 * geometry -- no amount of AF or blur-gate tuning fixes that; only a
 * smaller cutout (or a different camera) can.
 */
fun calculateMaxFingerFillRatio(
    sensorPhysicalSize: SizeF,
    focalLengthMM: Float,
    averageFingerWidthMM: Float,
    minFocusDistanceDiopters: Float
): Float {
    val sensorWidthMM = sensorPhysicalSize.width
    if (sensorWidthMM <= 0f || minFocusDistanceDiopters <= 0f) return 0f

    // Same "2 *" constant as calculateFingerDistance() -- kept identical
    // so this stays the exact algebraic inverse of that function, not a
    // second, subtly different formula.
    val fillRatio = 2 * focalLengthMM * averageFingerWidthMM * minFocusDistanceDiopters /
            (sensorWidthMM * 1000)

    Log.d(
        TAG,
        "MAX_FINGER_FILL -- at ${minFocusDistanceDiopters}D, finger can fill " +
                "at most ${fillRatio * 100}% of frame width"
    )

    return fillRatio
}
