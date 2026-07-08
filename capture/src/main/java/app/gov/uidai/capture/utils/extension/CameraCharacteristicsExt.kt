package app.gov.uidai.capture.utils.extension

import android.hardware.camera2.CameraCharacteristics
import android.util.Range

private const val TAG = "CameraCharacteristics"

fun CameraCharacteristics.getFPSRange(
    desiredUpperFPS: Int
): Range<Int>? {
    val available = this.get(
        CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
    ) ?: return null

    // Prefer fixed ranges at or below desired
    val fixedAtOrBelow = available
        .filter { it.lower == it.upper && it.upper <= desiredUpperFPS }
        .sortedByDescending { it.upper }
    if (fixedAtOrBelow.isNotEmpty()) return fixedAtOrBelow.first()

    // Otherwise, any range capped at or below desired, choose the highest upper
    val rangedAtOrBelow = available
        .filter { it.upper <= desiredUpperFPS }
        .sortedWith(compareByDescending<Range<Int>> { it.upper }.thenBy { it.upper - it.lower })
    if (rangedAtOrBelow.isNotEmpty()) return rangedAtOrBelow.first()

    // No ranges at or below desired — pick the lowest fixed above desired
    val fixedAbove = available
        .filter { it.lower == it.upper && it.upper >= desiredUpperFPS }
        .sortedBy { it.upper }
    if (fixedAbove.isNotEmpty()) return fixedAbove.first()

    // Fallback to the overall lowest upper bound
    return available.minByOrNull { it.upper }
}