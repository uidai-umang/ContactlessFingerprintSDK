package app.gov.uidai.capture.usecase.single

import android.os.SystemClock
import kotlin.math.abs

/**
 * Holds the last two YOLO finger-distance readings (mm).
 * - verdict(): direction from the LATEST reading (for the move closer / back message).
 * - isGoodAndStable(): both of the last two readings are inside the tolerance (for the capture gate).
 * Two misses in a row (no finger seen) clear the readings.
 */
class FingerDistanceTracker(
    private val targetMM: Float,
    private val toleranceMM: Float,
    private val maxAgeMs: Long = 6_000L,
    private val maxConsecutiveMisses: Int = 2
) {
    enum class Verdict { UNKNOWN, TOO_CLOSE, TOO_FAR, OK }

    private class Reading(val mm: Float, val atMs: Long)

    private val readings = ArrayList<Reading>(2)
    private var misses = 0

    @Synchronized
    fun record(mm: Float?) {
        if (mm == null || mm <= 0f) {
            misses++
            if (misses >= maxConsecutiveMisses) readings.clear()
            return
        }
        misses = 0
        readings.add(Reading(mm, SystemClock.uptimeMillis()))
        if (readings.size > 2) readings.removeAt(0)
    }

    @Synchronized
    fun verdict(): Verdict {
        val latest = readings.lastOrNull() ?: return Verdict.UNKNOWN
        if (SystemClock.uptimeMillis() - latest.atMs > maxAgeMs) return Verdict.UNKNOWN
        return when {
            latest.mm > targetMM + toleranceMM -> Verdict.TOO_FAR
            latest.mm < targetMM - toleranceMM -> Verdict.TOO_CLOSE
            else -> Verdict.OK
        }
    }

    @Synchronized
    fun isGoodAndStable(): Boolean {
        val now = SystemClock.uptimeMillis()
        return readings.size == 2 && readings.all {
            now - it.atMs <= maxAgeMs && abs(it.mm - targetMM) <= toleranceMM
        }
    }

    @Synchronized
    fun reset() {
        readings.clear()
        misses = 0
    }
}