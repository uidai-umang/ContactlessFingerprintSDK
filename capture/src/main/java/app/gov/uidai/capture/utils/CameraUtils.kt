package app.gov.uidai.capture.utils

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.Surface
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.tan


object CameraUtils {

    private const val TAG = "CameraUtils"

    fun getCameraId(cameraManager: CameraManager, facing: Int): String? {
        val cameraIds = cameraManager.cameraIdList.filter {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            lensFacing == facing
        }
        Log.i(TAG, "Camera Ids: $cameraIds")
        return cameraIds.firstOrNull()
    }

    fun getCameraCompatibility(characteristics: CameraCharacteristics): Boolean {
        val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        val (isAllowed, compatibility) = when (level) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> false to "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> false to "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> true to "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> true to "LEVEL_3"
            else-> false to "NA"
        }
        Log.i(TAG, "CameraCompatibility: $compatibility, IsAllowed: $isAllowed")
        return isAllowed
    }

    fun listAllCameraFocalInfo(cameraManager: CameraManager) {
        for (cameraId in cameraManager.cameraIdList) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                // Retrieve the array of supported focal lengths (millimeters)
                val focalLengths = characteristics
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.toList()
                    ?: emptyList()

                // Get minimum focus distance
                val minFocusDistance = diopterToDistance(
                    characteristics.get(
                        CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
                    ) ?: 0f
                )

                // Get hyperfocal distance
                val hyperfocalDistance = diopterToDistance(
                    characteristics.get(
                        CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE
                    ) ?: 0f
                )

                // Optional: add human-readable lens facing info
                val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }
                Log.i(
                    TAG,
                    "CameraFocalInfo ID=$cameraId ($facing): fl - $focalLengths mm, " +
                            "minfd - $minFocusDistance , hfd - $hyperfocalDistance"
                )
            } catch (e: Exception) {
                Log.w(TAG, "CameraFocalInfo Failed to query camera $cameraId", e)
            }
        }
    }

    fun getPreviewSize(context: Context, characteristics: CameraCharacteristics): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: return Size(1920, 1080)

        // Get display size for reference
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

        // Find the best size that:
        // 1. Has a reasonable resolution (not too small, not unnecessarily large)
        // 2. Has an aspect ratio close to screen ratio or common camera ratios
        val targetSizes = sizes.filter { size ->
            val aspectRatio = size.width.toFloat() / size.height.toFloat()
            val area = size.width * size.height

            // Filter by reasonable resolution (between 720p and 4K)
            area >= 1280 * 720 && area <= 3840 * 2160 &&
                    // Filter by reasonable aspect ratios (16:9, 4:3, or close to screen ratio)
                    (aspectRatio in 1.33f..1.78f)
        }.sortedWith(compareBy<Size> { size ->
            // Prefer sizes closer to screen dimensions
            val aspectRatio = size.width.toFloat() / size.height.toFloat()
            abs(aspectRatio - screenAspectRatio)
        }.thenBy { size ->
            // Among similar aspect ratios, prefer moderate resolution
            abs(size.width * size.height - screenWidth * screenHeight)
        })

        val selectedSize = targetSizes.firstOrNull() ?: sizes[0]
        Log.d(TAG, "Selected preview size: $selectedSize from ${sizes.size} available sizes")

        return selectedSize
    }

    fun getCaptureSizeMatchingPreview(
        characteristics: CameraCharacteristics,
        imageFormat: Int,
        previewSize: Size
    ): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val allCaptureSizes = map?.getOutputSizes(imageFormat)?.toList() ?: emptyList()

        if (allCaptureSizes.isEmpty()) {
            // Fallback if no sizes are available
            return previewSize
        }

        val previewAspectRatio = previewSize.width.toFloat() / previewSize.height

        // Find the best size that has an aspect ratio close to the preview's
        val bestSize = allCaptureSizes
            .sortedByDescending { it.width * it.height } // Prioritize higher resolution
            .minByOrNull {
                val ratio = it.width.toFloat() / it.height
                abs(previewAspectRatio - ratio) // Find minimum aspect ratio difference
            }

        return bestSize ?: allCaptureSizes.maxByOrNull { it.width * it.height }!!
    }

    fun getCaptureSize(characteristics: CameraCharacteristics, imageFormat: Int): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val allCaptureSizes = map?.getOutputSizes(imageFormat) ?: return Size(1920, 1080)

        // Pick the largest resolution available
        return allCaptureSizes.maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?: Size(1920, 1080)
    }

    fun getPreviewSizeMatchingCapture(
        context: Context,
        characteristics: CameraCharacteristics,
        captureSize: Size
    ): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: return captureSize

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Exact aspect ratio match check using cross multiplication (avoids float precision issues)
        val matchingAspectSizes = previewSizes.filter { size ->
            size.width * captureSize.height == size.height * captureSize.width
        }

        // Pick the preview size closest to screen dimensions
        return (matchingAspectSizes.ifEmpty { listOf(captureSize) })
            .filter { size ->
                size.width >= screenWidth && size.height >= screenWidth
            }
            .minByOrNull { size ->
                val diffW = abs(size.width - screenWidth)
                val diffH = abs(size.height - screenHeight)
                diffW + diffH
            } ?: captureSize
    }

    /**
     * Calculates the optimal focus distance and safe range dynamically,
     * correctly handling device orientation.
     *
     * This should be called once the UI is laid out and camera parameters are known.
     */
    fun calculateOptimalFocusDistance(
        sensorPhysicalSize: SizeF,
        viewFinderSize: Size,
        overlayViewSize: SizeF,
        deviceRotation: Int,
        focalLengthMM: Float,
        minFocusDistanceMM: Float,
        averageFingerWidthMM: Float
    ): Pair<Float, Float> {

        // This ratio is the key: What fraction of the preview's width does the overlay occupy?
        val overlayToPreviewRatio = (overlayViewSize.width * 0.8f) / viewFinderSize.width

        // Determine which sensor axis corresponds to the preview's width based on rotation
        val effectiveSensorDimensionForCalcMM =
            if (deviceRotation == Surface.ROTATION_0 || deviceRotation == Surface.ROTATION_180) {
                // --- DEVICE IS IN PORTRAIT ORIENTATION ---
                sensorPhysicalSize.height
            } else {
                // --- DEVICE IS IN LANDSCAPE ORIENTATION ---
                sensorPhysicalSize.width
            }

        // Now, apply the universal distance formula with the correctly chosen dimensions
        val optimalDistanceMm = 2 * (focalLengthMM * averageFingerWidthMM) /
                (overlayToPreviewRatio * effectiveSensorDimensionForCalcMM)

        // Create a "safe range" around this optimal distance (e.g., +/- 10%)
        val tolerance = 0.1f
        var minDistance = optimalDistanceMm * (1.0f - tolerance)
        // Also, ensure our minimum is not less than the hardware's minimum focus distance
        if (minDistance > 0) {
            minDistance = max(minDistance, minFocusDistanceMM)
        }
        val maxDistance = minDistance + 2 * optimalDistanceMm * tolerance
        Log.d(TAG, "FOCUS_THRESHOLD -- $optimalDistanceMm ($minDistance, $maxDistance)")
        return minDistance to maxDistance
    }

    fun diopterToDistance(diopter: Float): Float {
        return if (diopter > 0) 1.0f / diopter else Float.POSITIVE_INFINITY
    }
}